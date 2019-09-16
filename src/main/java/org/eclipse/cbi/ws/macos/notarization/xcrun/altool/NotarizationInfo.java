package org.eclipse.cbi.ws.macos.notarization.xcrun.altool;

import java.io.IOException;
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
import okhttp3.OkHttpClient;
import okhttp3.Request;

@AutoValue
public abstract class NotarizationInfo {

	private static final String APPLEID_PASSWORD_ENV_VAR_NAME = "APPLEID_PASSWORD";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(NotarizationInfo.class);

	abstract String appleIDUsername();

	abstract String appleIDPassword();

	abstract String appleRequestUUID();
	
	abstract Duration pollingTimeout();
	
	abstract OkHttpClient httpClient();
	
	public NotarizationInfoResult retrieveInfo() throws ExecutionException {
		List<String> cmd = ImmutableList.<String>builder().add("xcrun", "altool")
				.add("--notarization-info", appleRequestUUID().toString())
				.add("--output-format", "xml")
				.add("--username", appleIDUsername())
				.add("--password", "@env:" + APPLEID_PASSWORD_ENV_VAR_NAME)
				.build();

		ProcessBuilder processBuilder = new ProcessBuilder().command(cmd);
		processBuilder.environment().put(APPLEID_PASSWORD_ENV_VAR_NAME, appleIDPassword());

		NotarizationInfoResult.Builder resultBuilder = NotarizationInfoResult.builder();
		try(NativeProcess.Result result = NativeProcess.startAndWait(processBuilder, pollingTimeout())) {
			analyseResults(result, resultBuilder);
		} catch (IOException e) {
			LOGGER.error("Error while retrieving notarization info of request '" + appleRequestUUID() + "'", e);
			throw new ExecutionException("Failed to retrieve notarization info", e);
		} catch (TimeoutException e) {
			LOGGER.error("Timeout while retrieving notarization info of request '" + appleRequestUUID() + "'", e);
			throw new ExecutionException("Timeout while retrieving notarization info", e);
		}
		NotarizationInfoResult result = resultBuilder.build();
		LOGGER.trace("Notarization info retriever result:\n" + result.toString());
		return result;
	}

	public NotarizationInfoResult retrieveInfoFailsafe(Duration maxTotalDuration, Duration delayBetweenPolling, int maxFailedAttempt, Duration minBackOffDelay, Duration maxBackOffDelay) {
		RetryPolicy<NotarizationInfoResult> watchUntilCompleted = new RetryPolicy<NotarizationInfoResult>()
				.handleResultIf(info -> info.status() == NotarizationInfoResult.Status.NOTARIZATION_IN_PROGRESS)
				.withMaxAttempts(-1)
				.abortOn(Throwable.class)
				.withMaxDuration(maxTotalDuration)
				.withDelay(delayBetweenPolling)
				.onRetry(l -> LOGGER.trace("Retry fetching notarization info because it's still in progress (attempt#"+l.getAttemptCount()+", elaspedTime="+l.getElapsedTime()+"), lastResult:\n"+l.getLastResult()!=null?l.getLastResult().toString():"<null>" + ", lastFailure:\n"+l.getLastFailure()!=null?l.getLastFailure().toString():"<null>"));
		RetryPolicy<NotarizationInfoResult> retryOnFailure = new RetryPolicy<NotarizationInfoResult>()
				.handleResultIf(info -> info.status() == NotarizationInfoResult.Status.RETRIEVAL_FAILED)
				.withMaxAttempts(maxFailedAttempt)
				.withBackoff(minBackOffDelay.toNanos(), maxBackOffDelay.toNanos(), ChronoUnit.NANOS)
				.onRetry(l -> LOGGER.trace("Retry fetching notarization info because of previous error (attempt#"+l.getAttemptCount()+", elaspedTime="+l.getElapsedTime()+"), lastResult:\n"+l.getLastResult()!=null?l.getLastResult().toString():"<null>" + ", lastFailure:\n"+l.getLastFailure()!=null?l.getLastFailure().toString():"<null>"));
		return Failsafe.with(retryOnFailure, watchUntilCompleted)
				.onFailure(l -> LOGGER.error("Failure on notarization info retrieval attempt #" + l.getAttemptCount() + ", cause: " + l.getFailure().getMessage() + ", elapsed time: " + l.getElapsedTime(), l.getFailure()))
				.get(this::retrieveInfo);
	}
	
	private void analyseResults(NativeProcess.Result result, NotarizationInfoResult.Builder resultBuilder) throws ExecutionException {
		try {
			PListDict plistOutput = PListDict.fromXML(result.stdoutAsStream());

			if (result.exitValue() == 0) {
				Map<?, ?> notarizationInfoList = (Map<?, ?>) plistOutput.get("notarization-info");
				if (notarizationInfoList != null && !notarizationInfoList.isEmpty()) {
					parseNotarizationInfo(plistOutput, notarizationInfoList, resultBuilder);
				} else {
					LOGGER.error("Error while parsing notarization info plist file. Cannot find 'notarization-info' section");
					resultBuilder.status(NotarizationInfoResult.Status.RETRIEVAL_FAILED)
					.message("Error while parsing notarization info plist file. Cannot find 'notarization-info' section");
				}
			} else {
				parseProductError(plistOutput, resultBuilder, "Failed to notarize the requested file");
			}
		} catch (IOException | SAXException e) {
			LOGGER.error("Cannot parse notarization info for request '" + appleRequestUUID() + "'", e);
			throw new ExecutionException("Failed to retrieve notarization info.", e);
		} 
	}

	private void parseNotarizationInfo(PListDict plist, Map<?, ?> notarizationInfo, NotarizationInfoResult.Builder resultBuilder) {
		String statusStr = (String) notarizationInfo.get("Status");
		if ("success".equalsIgnoreCase(statusStr)) {
			resultBuilder
				.status(NotarizationInfoResult.Status.NOTARIZATION_SUCCESSFUL)
				.message("Notarization status: " + (String) notarizationInfo.get("Status Message"));
			String logFileUrl = (String)notarizationInfo.get("LogFileURL");
			if (logFileUrl != null) {
				resultBuilder.notarizationLog(logFromServer(logFileUrl));
			} else {
				LOGGER.warn("Unable to find LogFileURL in parsed plist file");
			}
		} else if ("in progress".equalsIgnoreCase(statusStr)) {
			resultBuilder
				.status(NotarizationInfoResult.Status.NOTARIZATION_IN_PROGRESS)
				.message("Notarization in progress");
		} else {
			parseProductError(plist, resultBuilder, "Failed to notarize the requested file (Status="+statusStr+")");

			String logFileUrl = (String)notarizationInfo.get("LogFileURL");
			if (logFileUrl != null) {
				resultBuilder.notarizationLog(logFromServer(logFileUrl));
			} else {
				LOGGER.warn("Unable to find LogFileURL in parsed plist file");
			}
		}
	}

	private NotarizationInfoResult.Builder parseProductError(PListDict plist, NotarizationInfoResult.Builder resultBuilder, String failureMessage) {
		Object rawProductErrors = plist.get("product-errors");

		if (rawProductErrors instanceof List) {
			List<?> productErrors = (List<?>) rawProductErrors;
			if (!productErrors.isEmpty()) {
				Object rawFirstError = productErrors.get(0);
				if (rawFirstError instanceof Map<?, ?>) {
					Map<?, ?> firstError = (Map<?, ?>) productErrors.get(0);
					if (firstError != null) {
						resultBuilder.message(failureMessage + ". Reason: " + firstError.get("message"));
					} else {
						resultBuilder.message(failureMessage + ". Reason: unable to parse the reason message [R3]");
					}
				} else {
					resultBuilder.message(failureMessage + ". Reason: unable to parse the reason message [R2]");
				}
			} else {
				resultBuilder.message(failureMessage + ". Reason: unable to parse the reason message [R2]");
			}
		} else {
			resultBuilder.message(failureMessage + ". Reason: unable to parse the reason message [R0]");
		}

		return resultBuilder.status(NotarizationInfoResult.Status.NOTARIZATION_FAILED);
	}

	private String logFromServer(String logFileUrl) {
		try {
			return httpClient().newCall(new Request.Builder().url(logFileUrl).build()).execute().body().string();
		} catch (IOException e) {
			LOGGER.error("Error while retrieving log from Apple server", e);
			return "Error while retrieving log from Apple server";
		}
	}
	
	public static Builder builder() {
		return new AutoValue_NotarizationInfo.Builder();
	}
	
	@AutoValue.Builder
	public static abstract class Builder {
		public abstract Builder appleIDUsername(String appleIDUsername);

		public abstract Builder appleIDPassword(String appleIDPassword);

		public abstract Builder appleRequestUUID(String appleRequestUUID);
		
		public abstract Builder pollingTimeout(Duration pollingTimeout);
		
		public abstract Builder httpClient(OkHttpClient httpClient);
		
		public abstract NotarizationInfo build();
	}
}
