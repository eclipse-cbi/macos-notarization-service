/*******************************************************************************
 * Copyright (c) 2023 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.execution;

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

import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizationInfoResult;
import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizationInfoResultBuilder;
import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;

public abstract class NotarizationTool {
    protected static final String APPLEID_PASSWORD_ENV_VAR_NAME = "APPLEID_PASSWORD";

    private static final Logger LOGGER = LoggerFactory.getLogger(NotarizationTool.class);
    private static final String TMPDIR = "TMPDIR";

    public boolean validate(NotarizationCredentials credentials) {
        return true;
    }

    public NotarizerResult upload(NotarizationCredentials credentials,
                                  String primaryBundleId,
                                  Path fileToNotarize,
                                  Duration uploadTimeout) throws ExecutionException, IOException {

        List<String> cmd = getUploadCommand(credentials, primaryBundleId, fileToNotarize);

        Path tempFolder =
                Files.createTempDirectory(fileToNotarize.getParent(),
                        com.google.common.io.Files.getNameWithoutExtension(fileToNotarize.toString())+ "-notarize-app-");

        ProcessBuilder processBuilder = new ProcessBuilder().command(cmd);
        if (credentials.hasPassword()) {
            processBuilder.environment().put(APPLEID_PASSWORD_ENV_VAR_NAME, credentials.getPassword());
        }
        processBuilder.environment().put(TMPDIR, tempFolder.toString());

        try(NativeProcess.Result nativeProcessResult = NativeProcess.startAndWait(processBuilder, uploadTimeout)) {
            NotarizerResult result = analyzeSubmissionResult(nativeProcessResult, fileToNotarize);
            LOGGER.trace("Notarization upload result:\n" + result.toString());
            return result;
        } catch (TimeoutException e) {
            LOGGER.error("Timeout happened during notarization upload of file " + fileToNotarize, e);
            throw new ExecutionException("Timeout happened during notarization upload", e);
        } catch (IOException e) {
            LOGGER.error("IOException happened during notarization upload of file " + fileToNotarize, e);
            throw new ExecutionException("IOException happened during notarization upload", e);
        } finally {
            if (Files.exists(tempFolder)) {
                LOGGER.trace("Deleting notarize-app temporary folder " + tempFolder);
                try (Stream<File> filesToDelete = Files.walk(tempFolder).sorted(Comparator.reverseOrder()).map(Path::toFile)) {
                    filesToDelete.forEach(File::delete);
                } catch (IOException e) {
                    LOGGER.warn("IOException happened during deletion of notarize-app temporary folder " + tempFolder, e);
                }
            }
        }
    }

    protected abstract List<String> getUploadCommand(NotarizationCredentials credentials,
                                                     String primaryBundleId,
                                                     Path fileToNotarize);

    protected abstract NotarizerResult analyzeSubmissionResult(NativeProcess.Result nativeProcessResult,
                                                               Path fileToNotarize) throws ExecutionException;

    public NotarizationInfoResult retrieveInfo(NotarizationCredentials credentials,
                                               String appleRequestUUID,
                                               Duration pollingTimeout) throws ExecutionException, IOException {

        List<String> cmd = getInfoCommand(credentials, appleRequestUUID);

        Path tempFolder = Files.createTempDirectory("-notarization-info-");

        ProcessBuilder processBuilder = new ProcessBuilder().command(cmd);
        if (credentials.hasPassword()) {
            processBuilder.environment().put(APPLEID_PASSWORD_ENV_VAR_NAME, credentials.getPassword());
        }
        processBuilder.environment().put(TMPDIR, tempFolder.toString());

        NotarizationInfoResultBuilder resultBuilder = NotarizationInfoResult.builder();
        try (NativeProcess.Result nativeProcessResult = NativeProcess.startAndWait(processBuilder, pollingTimeout)) {
            boolean addLog = analyzeInfoResult(nativeProcessResult, resultBuilder, appleRequestUUID);
            if (addLog && hasLogCommand()) {
                resultBuilder.notarizationLog(retrieveLog(credentials,
                                                          appleRequestUUID,
                                                          pollingTimeout));
            }
            NotarizationInfoResult result = resultBuilder.build();
            LOGGER.trace("Notarization info retriever result:\n{}", result);
            return result;
        } catch (IOException e) {
            LOGGER.error("Error while retrieving notarization info of request '" + appleRequestUUID + "'", e);
            throw new ExecutionException("Failed to retrieve notarization info", e);
        } catch (TimeoutException e) {
            LOGGER.error("Timeout while retrieving notarization info of request '" + appleRequestUUID + "'", e);
            throw new ExecutionException("Timeout while retrieving notarization info", e);
        } finally {
            LOGGER.trace("Deleting notarization-info temporary folder " + tempFolder);
            try (Stream<File> filesToDelete = Files.walk(tempFolder).sorted(Comparator.reverseOrder()).map(Path::toFile)) {
                filesToDelete.forEach(File::delete);
            } catch (IOException e) {
                LOGGER.warn("IOException happened during deletion of notarization-info temporary folder " + tempFolder, e);
            }
        }
    }

    protected abstract List<String> getInfoCommand(NotarizationCredentials credentials, String appleRequestUUID);

    protected abstract boolean analyzeInfoResult(NativeProcess.Result nativeProcessResult,
                                                 NotarizationInfoResultBuilder resultBuilder,
                                                 String appleRequestUUID) throws ExecutionException;

    public String retrieveLog(NotarizationCredentials credentials,
                              String appleRequestUUID,
                              Duration pollingTimeout) throws ExecutionException, IOException {

        List<String> cmd = getLogCommand(credentials, appleRequestUUID);

        Path tempFolder = Files.createTempDirectory("-notarization-log-");

        ProcessBuilder processBuilder = new ProcessBuilder().command(cmd);
        if (credentials.hasPassword()) {
            processBuilder.environment().put(APPLEID_PASSWORD_ENV_VAR_NAME, credentials.getPassword());
        }
        processBuilder.environment().put(TMPDIR, tempFolder.toString());

        try (NativeProcess.Result nativeProcessResult = NativeProcess.startAndWait(processBuilder, pollingTimeout)) {
            if (nativeProcessResult.exitValue() == 0) {
                return new String(nativeProcessResult.stdoutAsStream().readAllBytes());
            } else {
                LOGGER.error("Error while retrieving notarization log of request '" + appleRequestUUID + "'");
                throw new ExecutionException("Failed to retrieve notarization log", null);
            }
        } catch (IOException e) {
            LOGGER.error("Error while retrieving notarization log of request '" + appleRequestUUID + "'", e);
            throw new ExecutionException("Failed to retrieve notarization log", e);
        } catch (TimeoutException e) {
            LOGGER.error("Timeout while retrieving notarization log of request '" + appleRequestUUID + "'", e);
            throw new ExecutionException("Timeout while retrieving notarization log", e);
        } finally {
            LOGGER.trace("Deleting notarization-info temporary folder " + tempFolder);
            try (Stream<File> filesToDelete = Files.walk(tempFolder).sorted(Comparator.reverseOrder()).map(Path::toFile)) {
                filesToDelete.forEach(File::delete);
            } catch (IOException e) {
                LOGGER.warn("IOException happened during deletion of notarization-log temporary folder " + tempFolder, e);
            }
        }
    }

    protected abstract boolean hasLogCommand();

    protected abstract List<String> getLogCommand(NotarizationCredentials credentials, String appleRequestUUID);
}