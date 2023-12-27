/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.execution;

import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@RecordBuilder
public record Notarizer(
		String primaryBundleId,
		NotarizationCredentials credentials,
		Path fileToNotarize,
		Duration uploadTimeout,
		NotarizationTool tool) {

	private static final Logger LOGGER = LoggerFactory.getLogger(Notarizer.class);

	public static NotarizerBuilder builder() {
		return NotarizerBuilder.builder();
	}

	public NotarizerResult uploadFailsafe(int maxFailedAttempts, Duration minBackOffDelay, Duration maxBackOffDelay) {
		RetryPolicy<NotarizerResult> retryOnFailure =
			new RetryPolicy<NotarizerResult>()
				.handleResultIf(info -> info.status() == NotarizerResult.Status.UPLOAD_FAILED)
				.withMaxAttempts(maxFailedAttempts)
				.withBackoff(minBackOffDelay.toNanos(), maxBackOffDelay.toNanos(), ChronoUnit.NANOS)
				.onFailedAttempt(
					l -> LOGGER.trace(
							String.format("Failed to upload file for notarization because of failure " +
										  "(attempt#%d, elapsedTime=%s), lastResult:\n%s",
										  l.getAttemptCount(), l.getElapsedTime(), l.getLastResult()),
							l.getLastFailure())
				);

		return
			Failsafe.with(retryOnFailure)
				.onFailure(
					l -> LOGGER.error(
							String.format("Failure on notarization upload attempt #%d, cause: %s, elapsed time: %s",
									      l.getAttemptCount(), l.getFailure().getMessage(), l.getElapsedTime()),
							l.getFailure()))
				.get(() -> tool().upload(credentials(), primaryBundleId(), fileToNotarize(), uploadTimeout()));
	}
}
