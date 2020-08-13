/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.altool;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
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
				.abortOn(t -> !(t instanceof ExecutionException))
				.withMaxDuration(maxTotalDuration)
				.withDelay(delayBetweenPolling)
				.onFailedAttempt(l -> LOGGER.trace("Notarization is still in progress on Apple services (attempt#"+l.getAttemptCount()+", elaspedTime="+l.getElapsedTime()+"), lastResult:\n"+l.getLastResult() + ", lastFailure:\n"+l.getLastFailure()));
		RetryPolicy<NotarizationInfoResult> retryOnFailure = new RetryPolicy<NotarizationInfoResult>()
				.handleResultIf(info -> info.status() == NotarizationInfoResult.Status.RETRIEVAL_FAILED)
				.handleIf(t -> t instanceof ExecutionException)
				.withMaxAttempts(maxFailedAttempt)
				.withBackoff(minBackOffDelay.toNanos(), maxBackOffDelay.toNanos(), ChronoUnit.NANOS)
				.onFailedAttempt(l -> LOGGER.trace("Failed to fetch notarization info because of previous error (attempt#"+l.getAttemptCount()+", elaspedTime="+l.getElapsedTime()+"), lastResult:\n"+l.getLastResult() + ", lastFailure:\n"+l.getLastFailure()));
		return Failsafe.with(retryOnFailure, watchUntilCompleted)
				.onFailure(l -> LOGGER.error("Fail to fetch notarization info retrieval attempt #" + l.getAttemptCount() + ", cause: " + l.getFailure().getMessage() + ", elapsed time: " + l.getElapsedTime(), l.getFailure()))
				.get(this::retrieveInfo);
	}
	
	private void analyseResults(NativeProcess.Result result, NotarizationInfoResult.Builder resultBuilder) throws ExecutionException {
		try {
			PListDict plist = PListDict.fromXML(result.stdoutAsStream());

			if (result.exitValue() == 0) {
				Map<?, ?> notarizationInfoList = (Map<?, ?>) plist.get("notarization-info");
				if (notarizationInfoList != null && !notarizationInfoList.isEmpty()) {
					parseNotarizationInfo(plist, notarizationInfoList, resultBuilder);
				} else {
					LOGGER.error("Error while parsing notarization info plist file. Cannot find 'notarization-info' section");
					resultBuilder.status(NotarizationInfoResult.Status.RETRIEVAL_FAILED)
					.message("Error while parsing notarization info plist file. Cannot find 'notarization-info' section");
				}
			} else {
				resultBuilder.status(NotarizationInfoResult.Status.RETRIEVAL_FAILED);
				OptionalInt firstProductErrorCode = plist.firstProductErrorCode();
				if (firstProductErrorCode.isPresent()) {
					switch (firstProductErrorCode.getAsInt()) {
						case 1519: // Could not find the RequestUUID.
							resultBuilder.message("Error while retrieving notarization info from Apple service. Remote service could not find the RequestUUID");
							break;
						default:
							resultBuilder.message("Failed to notarize the requested file. Remote service error code = " + firstProductErrorCode.getAsInt() + " (xcrun altool exit value ="+result.exitValue()+").");
							break;
					}
				} else {
					Optional<String> errorMessage = plist.messageFromFirstProductError();
					if (errorMessage.isPresent()) {
						resultBuilder.message("Failed to notarize the requested file (xcrun altool exit value ="+result.exitValue()+"). Reason: " + errorMessage.get());
					} else {
						resultBuilder.message("Failed to notarize the requested file (xcrun altool exit value ="+result.exitValue()+").");
					}
				}
			}
		} catch (IOException | SAXException e) {
			LOGGER.error("Cannot parse notarization info for request '" + appleRequestUUID() + "'", e);
			throw new ExecutionException("Failed to retrieve notarization info.", e);
		} 
	}

	private void parseNotarizationInfo(PListDict plist, Map<?, ?> notarizationInfo, NotarizationInfoResult.Builder resultBuilder) {
		Object status = notarizationInfo.get("Status");
		if (status instanceof String) {
			String statusStr = (String) status;
			if ("success".equalsIgnoreCase(statusStr)) {
				resultBuilder
					.status(NotarizationInfoResult.Status.NOTARIZATION_SUCCESSFUL)
					.message("Notarization status: " + (String) notarizationInfo.get("Status Message"))
					.notarizationLog(extractLogFromServer(notarizationInfo));
			} else if ("in progress".equalsIgnoreCase(statusStr)) {
				resultBuilder
					.status(NotarizationInfoResult.Status.NOTARIZATION_IN_PROGRESS)
					.message("Notarization in progress");
			} else {
				resultBuilder
					.status(NotarizationInfoResult.Status.NOTARIZATION_FAILED)
					.notarizationLog(extractLogFromServer(notarizationInfo));

				Optional<String> errorMessage = plist.messageFromFirstProductError();
				OptionalInt errorCode = plist.firstProductErrorCode();
				resultBuilder.message("Failed to notarize the requested file (status="+statusStr+"). Error code="+errorCode+". Reason: " + errorMessage);
			}
		} else {
			throw new IllegalStateException("Cannot parse 'Status' from notarization-info");
		}
	}

	private String extractLogFromServer(Map<?, ?> notarizationInfo) {
		Object logFileUrl = notarizationInfo.get("LogFileURL");
		if (logFileUrl instanceof String) {
			return logFromServer((String) logFileUrl);
		} else {
			return "Unable to find LogFileURL in parsed plist file";
		}
	}

	private String logFromServer(String logFileUrl) {
		try {
			RetryPolicy<String> retryPolicy = new RetryPolicy<String>().withDelay(Duration.ofSeconds(10));
			return Failsafe.with(retryPolicy).get(() -> 
			httpClient().newCall(new Request.Builder().url(logFileUrl).build()).execute().body().string());
		} catch (FailsafeException e) {
			LOGGER.error("Error while retrieving log from Apple server", e.getCause());
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
