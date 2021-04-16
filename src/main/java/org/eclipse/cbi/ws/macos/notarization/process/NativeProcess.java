/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.process;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.auto.value.AutoValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NativeProcess {

	private static final Logger LOGGER = LoggerFactory.getLogger(NativeProcess.class);
	
	private static final int DESTROY_GRACETIME_MILLIS = 5000;

	public static Result startAndWait(ProcessBuilder processBuilder, Duration timeout) throws TimeoutException, IOException {
		Iterator<String> commandIterator = processBuilder.command().iterator();
		String arg0 = (commandIterator.hasNext() ? commandIterator.next() : "" )
		+ (commandIterator.hasNext() ? " " + commandIterator.next() : "")
		+ (commandIterator.hasNext() ? " " + commandIterator.next() : "");
		
		String safePrefix = arg0.replaceAll("[ /]", "-").replaceAll("-+", "-") + "-";
		Path out = Files.createTempFile(safePrefix, ".stdout");
		Path err = Files.createTempFile(safePrefix, ".stderr");
		
		processBuilder.redirectOutput(out.toFile()).redirectError(err.toFile());

		Process p = processBuilder.start();

		try {
			if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) { // timeout
				destroy(p, arg0, out, err);
				throw new TimeoutException("Process '" + arg0
						+ "' has been interrupted. It did not complete in less than " + timeout);
			}
		} catch (InterruptedException e) { // we've been interrupted
			LOGGER.error("Thread '" + Thread.currentThread().getName()
				+ "' has been interrupted while waiting for the process '" + arg0 + "' to complete.", e);
			
			destroy(p, arg0, out, err);

			// Restore the interrupted status
			Thread.currentThread().interrupt();
		}

		AutoValue_NativeProcess_Result result = new AutoValue_NativeProcess_Result(p.exitValue(), arg0, out, err);
		return result.log();
	}

	private static void destroy(Process p, String arg0, Path out, Path err) {
		p.destroy();
		try {
			if (!p.waitFor(DESTROY_GRACETIME_MILLIS, TimeUnit.MILLISECONDS)) {
				if (!p.destroyForcibly().waitFor(DESTROY_GRACETIME_MILLIS, TimeUnit.MILLISECONDS)) {
					LOGGER.error(
						"Process '" + arg0 + "' did not stop even after being forcibly destroyed. \n" 
								+ "Current stdout:\n" + stdioContent(out) + "\n"
								+ "Current stderr:\n" + stdioContent(err)+"\n");
				}
			}
		} catch (InterruptedException e) {
			LOGGER.error("Thread '" + Thread.currentThread().getName()
				+ "' has been interrupted while waiting for the process '" + arg0 + "' to be destroyed.", e);

			// Restore the interrupted status
			Thread.currentThread().interrupt();
		} finally {
			deleteIfExists(out);
			deleteIfExists(err);
		}
	}

	private static String stdioContent(Path stdio) {
		try (Stream<String> lineStream = Files.lines(stdio)) {
			return lineStream.collect(Collectors.joining("\n"));
		} catch (IOException e) {
			LOGGER.warn("Error while collecting content of '" + stdio + "'", e);
			return e.getMessage();
		}
	}

	static void deleteIfExists(Path path) {
		if (path != null) {
			try {
				Files.deleteIfExists(path);
			} catch (IOException e) {
				LOGGER.warn("Error while removing temporary file '" + path + "'", e);
			}
		}
	}

	@AutoValue
	public static abstract class Result implements AutoCloseable {
		public abstract int exitValue();

		public abstract String arg0();

		abstract Path stdout();

		public InputStream stdoutAsStream() throws IOException {
			return Files.newInputStream(stdout(), StandardOpenOption.READ);
		}

		abstract Path stderr();

		Result log() {
			LOGGER.trace(this.toString());
			if (exitValue() == 0) {
				LOGGER.trace("Process '" + arg0() + "' exited with value '" + exitValue() + "'");
				logstdio();
			} else {
				LOGGER.error("Process '" + arg0() + "' exited with value '" + exitValue() + "'");
				logstdio();
			}
			return this;
		}

		private void logstdio() {
			logstdio("stdout", stdout());
			logstdio("stderr", stderr());
		}

		private void logstdio(String stdioname, Path stdio) {
			try {
				if (Files.size(stdio) > 0) {
					if (exitValue() != 0) {
						LOGGER.error("Process '" + arg0() + "' "+stdioname+":\n" + stdioContent(stdio));
					} else {						
						LOGGER.debug("Process '" + arg0() + "' "+stdioname+":\n" + stdioContent(stdio));
					}
				} else {
					LOGGER.debug("Process '" + arg0() + "' exited with no content on "+stdioname);
				}
			} catch (IOException e) {
				LOGGER.warn("Error while logging "+stdioname+" for '" + arg0() + "'");
			}
		}

		@Override
		public void close() {
			deleteIfExists(stdout());
			deleteIfExists(stderr());
		}
	}
}