/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.cbi.common.util.Zips;
import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@RecordBuilder
public record Stapler(Path fileToStaple, Duration staplingTimeout) {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Stapler.class);
	private static final String DOT_APP_GLOB_PATTERN = "glob:**.{app,plugin,framework}";
	private static final String TMPDIR = "TMPDIR";

	public static StaplerBuilder builder() {
		return StaplerBuilder.builder();
	}
	
	public StaplerResult staple() throws ExecutionException, IOException {
		if ("zip".equals(com.google.common.io.Files.getFileExtension(fileToStaple().toString()))) {
			return stapleZipFile(fileToStaple());
		} else {
			return stapleFile(fileToStaple());
		}
	}

	private StaplerResult stapleZipFile(Path zipFile) throws ExecutionException {
		try {
			Path unzipFolder = zipFile.getParent().resolve(zipFile.getFileName().toString() + "-unzip");
			Zips.unpackZip(zipFile, unzipFolder);
			try (Stream<Path> pathStream = Files.list(unzipFolder)) {
				final PathMatcher dotAppPattern = unzipFolder.getFileSystem().getPathMatcher(DOT_APP_GLOB_PATTERN);
				List<StaplerResult> results = pathStream
					.filter(p -> Files.isDirectory(p) && dotAppPattern.matches(p))
					.map(p -> {
						try {
							return stapleFile(p);
						} catch (ExecutionException | IOException e) {
							LOGGER.error("Error while stapling a file from a zip", e);
							return new SimpleStaplerResult(StaplerResult.Status.ERROR, e.getMessage());
						}
					})
					.collect(Collectors.toList());
				if (Zips.packZip(unzipFolder, zipFile, false) <= 0) {
					throw new IOException("Something wrong happened when trying to zip it back after stapling zip content");
				}
				return StaplerResult.from(results);
			}
		} catch (IOException e) {
			LOGGER.error("Error while stapling notarization ticket to zip file " + zipFile, e);
			throw new ExecutionException("Error happened while stapling notarization ticket to the uploaded zip file", e);
		}
	}

	private StaplerResult stapleFile(Path file) throws ExecutionException, IOException {
		Path xcrunTempFolder = Files.createTempDirectory("-xcrun-stapler-");

		List<String> cmd =
			ImmutableList.<String>builder().add("xcrun", "stapler")
				.add("staple", file.toString())
				.build();
		
		ProcessBuilder processBuilder = new ProcessBuilder().command(cmd);
		processBuilder.environment().put(TMPDIR, xcrunTempFolder.toString());

		try(NativeProcess.Result nativeProcessResult = NativeProcess.startAndWait(processBuilder, staplingTimeout())) {
			if (nativeProcessResult.exitValue() == 0) {
				return new SimpleStaplerResult(StaplerResult.Status.SUCCESS,
						"Notarization ticket has been stapled to the uploaded file successfully");
			} else {
				return new SimpleStaplerResult(StaplerResult.Status.ERROR,
						"Error happened while stapling notarization ticket to the uploaded file");
			}
		} catch (IOException e) {
			LOGGER.error("Error while stapling notarization ticket to file " + file, e);
			throw new ExecutionException("Error happened while stapling notarization ticket to the uploaded file", e);
		} catch (TimeoutException e) {
			LOGGER.error("Timeout while stapling notarization ticket to file " + file, e);
			throw new ExecutionException("Timeout while stapling notarization ticket to the uploaded file", e);
		} finally {
			LOGGER.trace("Deleting xcrun-stapler temporary folder " + xcrunTempFolder);
			try (Stream<File> filesToDelete = Files.walk(xcrunTempFolder).sorted(Comparator.reverseOrder()).map(Path::toFile)) {
				filesToDelete.forEach(File::delete);
			} catch (IOException e) {
				LOGGER.warn("IOException happened during deletion of xcrun-stapler temporary folder " + xcrunTempFolder, e);
			}
		}
	}

	public StaplerResult stapleFailsafe(int maxFailedAttempts, Duration minBackOffDelay, Duration maxBackOffDelay) {
		RetryPolicy<StaplerResult> retryOnFailure = new RetryPolicy<StaplerResult>()
			.handleResultIf(info -> info.status() == StaplerResult.Status.ERROR)
			.withMaxAttempts(maxFailedAttempts)
			.withBackoff(minBackOffDelay.toNanos(), maxBackOffDelay.toNanos(), ChronoUnit.NANOS)
			.onFailedAttempt(
					l -> LOGGER.trace(
							String.format("Retry stapling notarization ticket because of failure " +
										  "(attempt#%d, elapsedTime=%s), lastResult:\n%s",
									      l.getAttemptCount(), l.getElapsedTime(), l.getLastResult()),
							l.getLastFailure()));

		return
			Failsafe.with(retryOnFailure)
				.onFailure(l -> LOGGER.error(String.format("Failure on notarization ticket stapling attempt #%d, cause: %s, elapsed time: %s",
								                           l.getAttemptCount(), l.getFailure().getMessage(), l.getElapsedTime()),
											 l.getFailure()))
				.get(this::staple);
	}
}
