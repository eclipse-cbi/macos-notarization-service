package org.eclipse.cbi.ws.macos.notarization.xcrun.altool;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

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
			PListDict plistOutput = PListDict.fromXML(nativeProcessResult.stdoutAsStream());
						
			if (nativeProcessResult.exitValue() == 0) {
				resultBuilder
					.status(NotarizerResult.Status.UPLOAD_SUCCESSFUL)
					.message((String) plistOutput.get("success-message"))
					.appleRequestUUID((String) ((Map<String,Object>) plistOutput.get("notarization-upload")).get("RequestUUID")); 
			} else {
				resultBuilder
					.status(NotarizerResult.Status.UPLOAD_FAILED)
					.message("Failed to notarize the requested file. Reason: " + (String) ((List<Map<String, Object>>) plistOutput.get("product-errors")).get(0).get("message"));
			}
			
		} catch (IOException | SAXException e) {
			LOGGER.error("Error while parsing the output after the upload of '" + fileToNotarize() + "' to the Apple notarization service", e);
			throw new ExecutionException("Error while parsing the output after the upload of the file to be notarized", e);
		}
		return resultBuilder.build();
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
