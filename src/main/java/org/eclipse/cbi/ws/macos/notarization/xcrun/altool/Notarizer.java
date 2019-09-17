package org.eclipse.cbi.ws.macos.notarization.xcrun.altool;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@AutoValue
public abstract class Notarizer {

	private static final Pattern UPLOADID_PATTERN = Pattern.compile(".*The upload ID is ([A-Za-z0-9\\\\-]*).*");
	private static final Pattern ERROR_MESSAGE_PATTERN = Pattern.compile(".*\"(.*)\".*");

	private static final String APPLEID_PASSWORD_ENV_VAR_NAME = "APPLEID_PASSWORD";

	private static final Logger LOGGER = LoggerFactory.getLogger(Notarizer.class);

	abstract String primaryBundleId();

	abstract String appleIDUsername();

	abstract String appleIDPassword();

	abstract Path fileToNotarize();

	abstract Duration uploadTimeout();

	public static Builder builder() {
		return new AutoValue_Notarizer.Builder();
	}

	public NotarizerResult upload() throws ExecutionException {
		List<String> cmd = ImmutableList.<String>builder()
				.add("xcrun", "altool")
				.add("--notarize-app")
				.add("--output-format", "xml")
				.add("--username", appleIDUsername())
				.add("--password", "@env:" + APPLEID_PASSWORD_ENV_VAR_NAME)
				.add("--primary-bundle-id", primaryBundleId())
				.add("--file", fileToNotarize().toString()).build();

		ProcessBuilder processBuilder = new ProcessBuilder().command(cmd);
		processBuilder.environment().put(APPLEID_PASSWORD_ENV_VAR_NAME, appleIDPassword());

		try(NativeProcess.Result nativeProcessResult = NativeProcess.startAndWait(processBuilder, uploadTimeout())) {
			NotarizerResult result = analyzeResult(nativeProcessResult);
			LOGGER.trace("Notarization upload result:\n" + result.toString());
			return result;
		} catch (TimeoutException e) {
			LOGGER.error("Timeout happened during notarization upload of file " + fileToNotarize(), e.getMessage());
			throw new ExecutionException("Timeout happened during notarization upload", e);
		} catch (IOException e) {
			LOGGER.error("IOException happened during notarization upload of file " + fileToNotarize(), e.getMessage());
			throw new ExecutionException("IOException happened during notarization upload", e);
		}
	}

	public NotarizerResult uploadFailsafe(int maxFailedAttempts, Duration minBackOffDelay, Duration maxBackOffDelay) {
		RetryPolicy<NotarizerResult> retryOnFailure = new RetryPolicy<NotarizerResult>()
				.handleResultIf(info -> info.status() == NotarizerResult.Status.UPLOAD_FAILED)
				.withMaxAttempts(maxFailedAttempts)
				.withBackoff(minBackOffDelay.toNanos(), maxBackOffDelay.toNanos(), ChronoUnit.NANOS)
				.onRetry(l -> LOGGER.trace("Retry uploading file for notarization because of failure (attempt#"+l.getAttemptCount()+", elaspedTime="+l.getElapsedTime()+"), lastResult:\n"+l.getLastResult()!=null?l.getLastResult().toString():"<null>" + ", lastFailure:\n"+l.getLastFailure()!=null?l.getLastFailure().toString():"<null>"));

		return Failsafe.with(retryOnFailure)
				.onFailure(l -> LOGGER.error("Failure on notarization upload attempt #" + l.getAttemptCount() + ", cause: " + l.getFailure().getMessage() + ", elapsed time: " + l.getElapsedTime(), l.getFailure()))
				.get(this::upload);
	}

	private NotarizerResult analyzeResult(NativeProcess.Result nativeProcessResult) throws ExecutionException {
		NotarizerResult.Builder resultBuilder = NotarizerResult.builder();
		try {
			PListDict plist = PListDict.fromXML(nativeProcessResult.stdoutAsStream());
			if (nativeProcessResult.exitValue() == 0) {
				Optional<String> requestUUID = plist.getRequestUUIDFromNotarizationUpload();
				if (requestUUID.isPresent()) {
					resultBuilder
						.status(NotarizerResult.Status.UPLOAD_SUCCESSFUL)
						.message((String) plist.get("success-message"))
						.appleRequestUUID(requestUUID.get());
				} else {
					throw new IllegalStateException("Cannot find the Apple request ID from response " + plist.toString());
				}
			} else if (nativeProcessResult.exitValue() == 176) { // 176 seems to mean remote error
				analyzeExitValue176(plist, resultBuilder);
			} else {
				Optional<String> productErrors = plist.getFirstMessageFromProductErrors();
				if (productErrors.isPresent()) {
					resultBuilder
					.status(NotarizerResult.Status.UPLOAD_FAILED)
					.message("Failed to notarize the requested file. Reason: " + productErrors.get());
				} else {
					resultBuilder
					.status(NotarizerResult.Status.UPLOAD_FAILED)
					.message("Failed to notarize the requested file. Reason: xcrun altool exit value was " + nativeProcessResult.exitValue());
				}
			}
		} catch (IOException | SAXException e) {
			LOGGER.error("Error while parsing the output after the upload of '" + fileToNotarize() + "' to the Apple notarization service", e);
			throw new ExecutionException("Error while parsing the output after the upload of the file to be notarized", e);
		}
		return resultBuilder.build();
	}

	private void analyzeExitValue176(PListDict plist, NotarizerResult.Builder resultBuilder) {
		Optional<String> rawErrorMessage = plist.getFirstMessageFromProductErrors();
		if (rawErrorMessage.isPresent()) {
			String errorMessage = rawErrorMessage.get();
			if (errorMessage.contains("ITMS-4302")) { // ERROR ITMS-4302: "The software asset has an invalid primary bundle identifier '{}'"
				resultBuilder.status(NotarizerResult.Status.UPLOAD_FAILED);
				Matcher matcher = ERROR_MESSAGE_PATTERN.matcher(errorMessage);
				if (matcher.matches()) {
					resultBuilder.message(matcher.group(1));
				} else {
					resultBuilder.message("The software asset has an invalid primary bundle identifier");
				}
			} else if (errorMessage.contains("ITMS-90732")) { // ERROR ITMS-90732: "The software asset has already been uploaded. The upload ID is {}"
				Optional<String> appleRequestID = parseAppleRequestID(errorMessage);
				if (appleRequestID.isPresent()) {
					resultBuilder.status(NotarizerResult.Status.UPLOAD_SUCCESSFUL)
					.message("Notarization in progress (software asset has been already previously uploaded to Apple notarization service)")
					.appleRequestUUID(appleRequestID.get());
				} else {
					throw new IllegalStateException("Cannot parse the Apple request ID from error message while error is ITMS-90732");
				}
			} else {
				resultBuilder.status(NotarizerResult.Status.UPLOAD_FAILED)
				.message("Failed to notarize the requested file. Reason: " + errorMessage);
			}
		}
	}

	private Optional<String> parseAppleRequestID(String message) {
		try {
			Matcher matcher = UPLOADID_PATTERN.matcher(message);
			if (matcher.matches()) {
				return Optional.of(matcher.group(1));
			}
		} catch (IllegalStateException e) {
			LOGGER.info("No Apple request ID in xcrun output", e);
		}
		return Optional.empty();
	}

	@AutoValue.Builder
	public static abstract class Builder {
		public abstract Builder primaryBundleId(String primaryBundleId);

		public abstract Builder appleIDUsername(String appleIDUsername);

		public abstract Builder appleIDPassword(String appleIDPassword);

		public abstract Builder fileToNotarize(Path fileToNotarize);

		public abstract Builder uploadTimeout(Duration timeout);

		public abstract Notarizer build();
	}
}
