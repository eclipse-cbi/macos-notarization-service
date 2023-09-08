/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.common;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;

import com.google.auto.value.AutoValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import okhttp3.OkHttpClient;

@AutoValue
public abstract class NotarizationInfo {

	private static final Logger LOGGER = LoggerFactory.getLogger(NotarizationInfo.class);

	abstract String appleIDUsername();

	abstract String appleIDPassword();

	abstract String appleRequestUUID();

	abstract Duration pollingTimeout();

	abstract OkHttpClient httpClient();

	abstract NotarizationTool tool();

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
				.get(() -> tool().retrieveInfo(appleIDUsername(), appleIDPassword(), appleRequestUUID(), pollingTimeout(), httpClient()));
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

		public abstract Builder tool(NotarizationTool tool);

		public abstract NotarizationInfo build();
	}
}
