/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.common;

import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import com.google.auto.value.AutoValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@AutoValue
public abstract class Notarizer {

	private static final Logger LOGGER = LoggerFactory.getLogger(Notarizer.class);

	abstract String primaryBundleId();

	abstract String appleIDUsername();

	abstract String appleIDPassword();

	abstract String appleIDTeamID();

	abstract Path fileToNotarize();

	abstract Duration uploadTimeout();

	abstract NotarizationTool tool();

	public static Builder builder() {
		return new AutoValue_Notarizer.Builder();
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

		return Failsafe.with(retryOnFailure)
				.onFailure(
					l -> LOGGER.error(
							String.format("Failure on notarization upload attempt #%d, cause: %s, elapsed time: %s",
									      l.getAttemptCount(), l.getFailure().getMessage(), l.getElapsedTime()),
							l.getFailure()))
				.get(() -> tool().upload(appleIDUsername(), appleIDPassword(), appleIDTeamID(), primaryBundleId(), fileToNotarize(), uploadTimeout()));
	}

	@AutoValue.Builder
	public static abstract class Builder {
		public abstract Builder primaryBundleId(String primaryBundleId);

		public abstract Builder appleIDUsername(String appleIDUsername);

		public abstract Builder appleIDPassword(String appleIDPassword);

		public abstract Builder appleIDTeamID(String appleIDTeamID);

		public abstract Builder fileToNotarize(Path fileToNotarize);

		public abstract Builder uploadTimeout(Duration timeout);

		public abstract Builder tool(NotarizationTool tool);

		public abstract Notarizer build();
	}
}
