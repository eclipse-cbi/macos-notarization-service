/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.altool;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@AutoValue
public abstract class Stapler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Notarizer.class);

	abstract Path fileToStaple();
	
	abstract Duration staplingTimeout();
	
	public static Builder builder() {
		return new AutoValue_Stapler.Builder();
	}
	
	public StaplerResult staple() throws ExecutionException {
		List<String> cmd = ImmutableList.<String>builder().add("xcrun", "stapler")
				.add("staple", fileToStaple().toString())
				.build();
		
		ProcessBuilder processBuilder = new ProcessBuilder().command(cmd);

		try(NativeProcess.Result nativeProcessResult = NativeProcess.startAndWait(processBuilder, staplingTimeout())) {
			if (nativeProcessResult.exitValue() == 0) {
				return new AutoValue_StaplerResult(StaplerResult.Status.SUCCESS, "Notarization ticket has been stapled to the uploaded file successfully");
			} else {
				return new AutoValue_StaplerResult(StaplerResult.Status.ERROR, "Error happened while stapling notarization ticket to the uploaded file");
			}
		} catch (IOException e) {
			LOGGER.error("Error while stapling notarization ticket to file " + fileToStaple(), e.getMessage());
			throw new ExecutionException("Error happened while stapling notarization ticket to the uploaded file", e);
		} catch (TimeoutException e) {
			LOGGER.error("Timeout while stapling notarization ticket to file " + fileToStaple(), e.getMessage());
			throw new ExecutionException("Timeout while stapling notarization ticket to the uploaded file", e);
		}
	}
	
	public StaplerResult stapleFailsafe(int maxFailedAttempts, Duration minBackOffDelay, Duration maxBackOffDelay) {
		RetryPolicy<StaplerResult> retryOnFailure = new RetryPolicy<StaplerResult>()
				.handleResultIf(info -> info.status() == StaplerResult.Status.ERROR)
				.withMaxAttempts(maxFailedAttempts)
				.withBackoff(minBackOffDelay.toNanos(), maxBackOffDelay.toNanos(), ChronoUnit.NANOS)
				.onFailedAttempt(l -> LOGGER.trace("Retry stapling notarization ticket because of failure (attempt#"+l.getAttemptCount()+", elaspedTime="+l.getElapsedTime()+"), lastResult:\n"+l.getLastResult() + ", lastFailure:\n"+l.getLastFailure()));
		
		return Failsafe.with(retryOnFailure)
				.onFailure(l -> LOGGER.error("Failure on notarization ticket stapling attempt #" + l.getAttemptCount() + ", cause: " + l.getFailure().getMessage() + ", elapsed time: " + l.getElapsedTime(), l.getFailure()))
				.get(this::staple);
	}
	
	@AutoValue.Builder
	public static abstract class Builder {
		public abstract Builder fileToStaple(Path fileToStaple);
		public abstract Builder staplingTimeout(Duration staplingTimeout);
		public abstract Stapler build();
	}
}
