/*******************************************************************************
 * Copyright (c) 2023 Eclipse Foundation and others.
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
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;

public abstract class NotarizationTool {
    protected static final String APPLEID_PASSWORD_ENV_VAR_NAME = "APPLEID_PASSWORD";

    private static final Logger LOGGER = LoggerFactory.getLogger(NotarizationTool.class);
    private static final String TMPDIR = "TMPDIR";

    public NotarizerResult upload(String appleIDUsername,
                                  String appleIDPassword,
                                  String appleIDTeamID,
                                  String primaryBundleId,
                                  Path fileToNotarize,
                                  Duration uploadTimeout) throws ExecutionException, IOException {

        List<String> cmd = getUploadCommand(appleIDUsername, appleIDPassword, appleIDTeamID, primaryBundleId, fileToNotarize);

        Path xcrunTempFolder =
                Files.createTempDirectory(fileToNotarize.getParent(),
                        com.google.common.io.Files.getNameWithoutExtension(fileToNotarize.toString())+ "-xcrun-notarize-app-");

        ProcessBuilder processBuilder = new ProcessBuilder().command(cmd);
        processBuilder.environment().put(APPLEID_PASSWORD_ENV_VAR_NAME, appleIDPassword);
        processBuilder.environment().put(TMPDIR, xcrunTempFolder.toString());

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
            if (Files.exists(xcrunTempFolder)) {
                LOGGER.trace("Deleting xcrun-notarize-app temporary folder " + xcrunTempFolder);
                try (Stream<File> filesToDelete = Files.walk(xcrunTempFolder).sorted(Comparator.reverseOrder()).map(Path::toFile)) {
                    filesToDelete.forEach(File::delete);
                } catch (IOException e) {
                    LOGGER.warn("IOException happened during deletion of xcrun-notarize-app temporary folder " + xcrunTempFolder, e);
                }
            }
        }
    }

    protected abstract List<String> getUploadCommand(String appleIDUsername,
                                                     String appleIDPassword,
                                                     String appleIDTeamID,
                                                     String primaryBundleId,
                                                     Path fileToNotarize);

    protected abstract NotarizerResult analyzeSubmissionResult(NativeProcess.Result nativeProcessResult,
                                                               Path fileToNotarize) throws ExecutionException;

    public NotarizationInfoResult retrieveInfo(String appleIDUsername,
                                               String appleIDPassword,
                                               String appleIDTeamID,
                                               String appleRequestUUID,
                                               Duration pollingTimeout) throws ExecutionException, IOException {

        List<String> cmd = getInfoCommand(appleIDUsername, appleIDPassword, appleIDTeamID, appleRequestUUID);

        Path xcrunTempFolder = Files.createTempDirectory("-xcrun-notarization-info-");

        ProcessBuilder processBuilder = new ProcessBuilder().command(cmd);
        processBuilder.environment().put(APPLEID_PASSWORD_ENV_VAR_NAME, appleIDPassword);
        processBuilder.environment().put(TMPDIR, xcrunTempFolder.toString());

        NotarizationInfoResultBuilder resultBuilder = NotarizationInfoResult.builder();
        try (NativeProcess.Result nativeProcessResult = NativeProcess.startAndWait(processBuilder, pollingTimeout)) {
            boolean addLog = analyzeInfoResult(nativeProcessResult, resultBuilder, appleRequestUUID);
            if (addLog && hasLogCommand()) {
                resultBuilder.notarizationLog(retrieveLog(appleIDUsername,
                                                          appleIDPassword,
                                                          appleIDTeamID,
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
            LOGGER.trace("Deleting xcrun-notarization-info temporary folder " + xcrunTempFolder);
            try (Stream<File> filesToDelete = Files.walk(xcrunTempFolder).sorted(Comparator.reverseOrder()).map(Path::toFile)) {
                filesToDelete.forEach(File::delete);
            } catch (IOException e) {
                LOGGER.warn("IOException happened during deletion of xcrun-notarization-info temporary folder " + xcrunTempFolder, e);
            }
        }
    }

    protected abstract List<String> getInfoCommand(String appleIDUsername, String appleIDPassword, String appleIDTeamID, String appleRequestUUID);

    protected abstract boolean analyzeInfoResult(NativeProcess.Result nativeProcessResult,
                                                 NotarizationInfoResultBuilder resultBuilder,
                                                 String appleRequestUUID) throws ExecutionException;

    public String retrieveLog(String appleIDUsername,
                              String appleIDPassword,
                              String appleIDTeamID,
                              String appleRequestUUID,
                              Duration pollingTimeout) throws ExecutionException, IOException {

        List<String> cmd = getLogCommand(appleIDUsername, appleIDPassword, appleIDTeamID, appleRequestUUID);

        Path xcrunTempFolder = Files.createTempDirectory("-xcrun-notarization-log-");

        ProcessBuilder processBuilder = new ProcessBuilder().command(cmd);
        processBuilder.environment().put(APPLEID_PASSWORD_ENV_VAR_NAME, appleIDPassword);
        processBuilder.environment().put(TMPDIR, xcrunTempFolder.toString());

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
            LOGGER.trace("Deleting xcrun-notarization-info temporary folder " + xcrunTempFolder);
            try (Stream<File> filesToDelete = Files.walk(xcrunTempFolder).sorted(Comparator.reverseOrder()).map(Path::toFile)) {
                filesToDelete.forEach(File::delete);
            } catch (IOException e) {
                LOGGER.warn("IOException happened during deletion of xcrun-notarization-log temporary folder " + xcrunTempFolder, e);
            }
        }
    }

    protected abstract boolean hasLogCommand();

    protected abstract List<String> getLogCommand(String appleIDUsername, String appleIDPassword, String appleIDTeamID, String appleRequestUUID);
}