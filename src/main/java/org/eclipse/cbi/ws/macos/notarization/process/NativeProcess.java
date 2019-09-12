package org.eclipse.cbi.ws.macos.notarization.process;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.value.AutoValue;

public class NativeProcess {

	private static final Logger LOGGER = LoggerFactory.getLogger(NativeProcess.class);
	
	private static final int DESTROY_FORCIBLY_GRACETIME_MILLIS = 500;

	public static Result startAndWait(ProcessBuilder processBuilder, Duration timeout) throws TimeoutException, IOException {
		String arg0 = processBuilder.command().iterator().next();
		
		Path out = Files.createTempFile(arg0 + "-", ".stdout");
		Path err = Files.createTempFile(arg0 + "-", ".stderr");
		
		processBuilder.redirectOutput(out.toFile()).redirectError(err.toFile());

		Process p = processBuilder.start();

		try {
			if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) { // timeout
				p.destroyForcibly();
				throw new TimeoutException("Process '" + arg0
						+ "' has been stopped forcibly. It did not complete in less than " + timeout);
			}
		} catch (InterruptedException e) { // we've been interrupted
			LOGGER.error("Thread '" + Thread.currentThread().getName()
					+ "' has been interrupted while waiting for the process '" + arg0 + "' to complete.", e);
			
			try {
				// kill the subprocess and wait a bit before checking if it has been destroyed for real
				if (p.destroyForcibly().waitFor(DESTROY_FORCIBLY_GRACETIME_MILLIS, TimeUnit.MILLISECONDS) && !p.isAlive()) {
					return new AutoValue_NativeProcess_Result(p.exitValue(), arg0, out, err);
				} else {
					LOGGER.error(
							"Process '" + arg0 + "' did not stop even after being forcibly destroyed. Current output:\n"
									+ firstLinesAsString(out, 32));
					throw new RuntimeException(
							"Process '" + arg0 + "' did not stop even after being forcibly destroyed");
				}
			} catch (@SuppressWarnings("unused") InterruptedException e1) { // we've been interrupted, again (!)
				// we will restore the interrupted status soon.
			}

			// Restore the interrupted status
			Thread.currentThread().interrupt();
		}

		@SuppressWarnings("resource")
		AutoValue_NativeProcess_Result result = new AutoValue_NativeProcess_Result(p.exitValue(), arg0, out, err);
		return result.log();
	}

	static String firstLinesAsString(Path path, int limit) {
		String lines = "";
		try {
			lines = Files.lines(path).limit(limit).collect(Collectors.joining("\n"));
			if (Files.lines(path).limit(limit + 1).count() > limit) {
				return lines + "\n... Trimmed content. Enable 'trace' level logging to see full output\n";
			}
		} catch (IOException e) {
			LOGGER.warn("Error while logging the "+limit+" first lines of '" + path + "'");
		}
		return lines;
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

		private String stdioContent(Path stdio) throws IOException {
			final String content;
			if (LOGGER.isTraceEnabled()) {
				content = Files.lines(stdio).collect(Collectors.joining("\n"));
			} else if (exitValue() != 0 || LOGGER.isDebugEnabled()) {
				content = firstLinesAsString(stdio, 32);
			} else {
				content = "Logic error during " + NativeProcess.class.getName() + " logging. This content should never be printed!";
			}
			return content;
		}
		
		@Override
		public void close() {
			deleteIfExists(stdout());
			deleteIfExists(stderr());
		}

		private static void deleteIfExists(Path path) {
			if (path != null) {
				try {
					Files.deleteIfExists(path);
				} catch (IOException e) {
					LOGGER.warn("Error while removing temporary file '" + path + "'", e);
				}
			}
		}
	}
}