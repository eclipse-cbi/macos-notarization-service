/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.execution;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;

import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizationInfoResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public record NotarizationInfo(
		NotarizationCredentials credentials,
		String appleRequestUUID,
		Duration pollingTimeout,
		NotarizationTool tool) {

	private static final Logger LOGGER = LoggerFactory.getLogger(NotarizationInfo.class);

	public static NotarizationInfoBuilder builder() {
		return NotarizationInfoBuilder.builder();
	}

	public NotarizationInfoResult retrieveInfoFailsafe(Duration maxTotalDuration,
													   Duration delayBetweenPolling,
													   int maxFailedAttempt,
													   Duration minBackOffDelay,
													   Duration maxBackOffDelay) {
		RetryPolicy<NotarizationInfoResult> watchUntilCompleted =
			new RetryPolicy<NotarizationInfoResult>()
				.handleResultIf(info -> info.status() == NotarizationInfoResult.Status.NOTARIZATION_IN_PROGRESS)
				.withMaxAttempts(-1)
				.abortOn(t -> !(t instanceof ExecutionException))
				.withMaxDuration(maxTotalDuration)
				.withDelay(delayBetweenPolling)
				.onFailedAttempt(l ->
					LOGGER.trace("Notarization is still in progress on Apple services (attempt#"+l.getAttemptCount()+", " +
							 	 "elapsedTime="+l.getElapsedTime()+"), lastResult:\n"+l.getLastResult() +
							     ", lastFailure:\n"+l.getLastFailure()));

		RetryPolicy<NotarizationInfoResult> retryOnFailure =
			new RetryPolicy<NotarizationInfoResult>()
				.handleResultIf(info -> info.status() == NotarizationInfoResult.Status.RETRIEVAL_FAILED)
				.handleIf(t -> t instanceof ExecutionException)
				.withMaxAttempts(maxFailedAttempt)
				.withBackoff(minBackOffDelay.toNanos(), maxBackOffDelay.toNanos(), ChronoUnit.NANOS)
				.onFailedAttempt(l ->
					LOGGER.trace("Failed to fetch notarization info because of previous error (attempt#"+l.getAttemptCount()+", elapsedTime=" +
							     l.getElapsedTime()+"), lastResult:\n"+l.getLastResult() + ", lastFailure:\n"+l.getLastFailure()));

		return Failsafe.with(retryOnFailure, watchUntilCompleted)
				.onFailure(l ->
					LOGGER.error("Fail to fetch notarization info retrieval attempt #" + l.getAttemptCount() + ", cause: " +
							     l.getFailure().getMessage() + ", elapsed time: " + l.getElapsedTime(), l.getFailure()))
				.get(() -> tool().retrieveInfo(credentials(), appleRequestUUID(), pollingTimeout()));
	}
}
