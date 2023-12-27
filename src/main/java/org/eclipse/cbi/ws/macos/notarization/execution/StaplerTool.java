/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.execution;

import org.eclipse.cbi.ws.macos.notarization.execution.result.SimpleStaplerResult;
import org.eclipse.cbi.ws.macos.notarization.execution.result.StaplerResult;
import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

public abstract class StaplerTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaplerTool.class);
    private static final String TMPDIR = "TMPDIR";

    public StaplerResult stapleFile(Path file, Duration staplingTimeout) throws ExecutionException, IOException {
        Path tempFolder = Files.createTempDirectory("-stapler-");

        List<String> cmd = getStapleCommand(file);

        ProcessBuilder processBuilder = new ProcessBuilder().command(cmd);
        processBuilder.environment().put(TMPDIR, tempFolder.toString());

        try(NativeProcess.Result nativeProcessResult = NativeProcess.startAndWait(processBuilder, staplingTimeout)) {
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
            LOGGER.trace("Deleting stapler temporary folder " + tempFolder);
            try (Stream<File> filesToDelete = Files.walk(tempFolder).sorted(Comparator.reverseOrder()).map(Path::toFile)) {
                filesToDelete.forEach(File::delete);
            } catch (IOException e) {
                LOGGER.warn("IOException happened during deletion of stapler temporary folder " + tempFolder, e);
            }
        }
    }

    protected abstract List<String> getStapleCommand(Path fileToStaple);
}
