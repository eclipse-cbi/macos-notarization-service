/*******************************************************************************
 * Copyright (c) 2023 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.execution.generic;

import com.google.common.collect.ImmutableList;
import org.eclipse.cbi.ws.macos.notarization.execution.NotarizationCredentials;
import org.eclipse.cbi.ws.macos.notarization.execution.NotarizationTool;
import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizationInfoResult;
import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizationInfoResultBuilder;
import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizerResult;
import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizerResultBuilder;
import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RustNotarizer extends NotarizationTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(RustNotarizer.class);

    private static final Pattern SUBMISSION_ID_PATTERN = Pattern.compile("^created submission ID: ([0-9a-f\\-]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern POLL_STATE_PATTERN = Pattern.compile("poll state after [0-9]+s: (.+)", Pattern.CASE_INSENSITIVE);
    private static final String NOTARY_LOG_PREFIX = "notary log> ";

    @Override
    public boolean validate(NotarizationCredentials credentials) {
        return credentials.requireAppleApiKeyFile();
    }

    @Override
    protected List<String> getUploadCommand(NotarizationCredentials credentials, String primaryBundleId, Path fileToNotarize) {
        return ImmutableList.<String>builder()
                .add("rcodesign")
                .add("notary-submit")
                .add("--api-key-file", credentials.getAppleApiKeyFile())
                .add("--max-wait-seconds", "1")
                .add(fileToNotarize.toString()).build();
    }

    @Override
    protected NotarizerResult analyzeSubmissionResult(NativeProcess.Result nativeProcessResult, Path fileToNotarize) throws ExecutionException {
        NotarizerResultBuilder resultBuilder = NotarizerResult.builder();
        try {
            if (nativeProcessResult.exitValue() == 0) {
                String commandOutput = new String(nativeProcessResult.stderrAsStream().readAllBytes());
                String submissionId = null;
                for (String line : commandOutput.split("\n")) {
                    Matcher matcher = SUBMISSION_ID_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        submissionId = matcher.group(1);
                        LOGGER.debug("parsed submission id: " + submissionId);
                        resultBuilder
                                .status(NotarizerResult.Status.UPLOAD_SUCCESSFUL)
                                .message(line)
                                .appleRequestUUID(submissionId);
                        break;
                    }
                }

                if (submissionId == null) {
                    throw new IllegalStateException("Cannot find the Apple submission ID in command output: " + commandOutput);
                }
            } else {
                resultBuilder
                        .status(NotarizerResult.Status.UPLOAD_FAILED)
                        .message("Failed to notarize the requested file. Reason: ");
            }
        } catch (IOException e) {
            LOGGER.error("Error while parsing the output after the upload of '" + fileToNotarize + "' to the Apple notarization service", e);
            throw new ExecutionException("Error while parsing the output after the upload of the file to be notarized", e);
        }

        return resultBuilder.build();
    }

    @Override
    protected List<String> getInfoCommand(NotarizationCredentials credentials, String appleRequestUUID) {
        return ImmutableList.<String>builder()
                .add("rcodesign")
                .add("notary-wait")
                .add("--api-key-file", credentials.getAppleApiKeyFile())
                .add("--max-wait-seconds", "1")
                .add(appleRequestUUID).build();
    }

    @Override
    protected boolean analyzeInfoResult(NativeProcess.Result nativeProcessResult, NotarizationInfoResultBuilder resultBuilder, String appleRequestUUID) throws ExecutionException {
        try {
            if (nativeProcessResult.exitValue() == 0) {
                String commandOutput = new String(nativeProcessResult.stderrAsStream().readAllBytes());
                String statusStr = null;
                for (String line : commandOutput.split("\n")) {
                    Matcher matcher = POLL_STATE_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        statusStr = matcher.group(1);
                        LOGGER.debug("parsed status: " + statusStr);
                    }
                }

                if (statusStr == null) {
                    throw new IllegalStateException("Cannot find the submission status in command output: " + commandOutput);
                } else {
                    if ("Accepted".equalsIgnoreCase(statusStr)) {
                        resultBuilder
                                .status(NotarizationInfoResult.Status.NOTARIZATION_SUCCESSFUL)
                                .notarizationLog(extractNotarizationLog(commandOutput))
                                .message("Notarization status: " + statusStr);
                    } else if ("InProgress".equalsIgnoreCase(statusStr)) {
                        resultBuilder
                                .status(NotarizationInfoResult.Status.NOTARIZATION_IN_PROGRESS)
                                .message("Notarization in progress");
                    } else {
                        resultBuilder.status(NotarizationInfoResult.Status.NOTARIZATION_FAILED);

                        String errorMessage = "";
                        String errorCode = "";
                        resultBuilder.message("Failed to notarize the requested file (status="+statusStr+"). Error code="+errorCode+". Reason: " + errorMessage);
                    }
                }
            } else {
                resultBuilder
                        .status(NotarizationInfoResult.Status.RETRIEVAL_FAILED)
                        .message("Failed to notarize the requested file. Reason: ");
            }
        } catch (IOException e) {
            LOGGER.error("Cannot parse notarization info for request '" + appleRequestUUID + "'", e);
            throw new ExecutionException("Failed to retrieve notarization info.", e);
        }

        return false;
    }

    private String extractNotarizationLog(String commandOutput) {
        List<String> log = new ArrayList<>();

        for (String line : commandOutput.split("\n")) {
            if (line.startsWith(NOTARY_LOG_PREFIX)) {
                log.add(line.replaceFirst(NOTARY_LOG_PREFIX, ""));
            }
        }

        return String.join("\n", log);
    }

    @Override
    protected boolean hasLogCommand() {
        return false;
    }

    @Override
    protected List<String> getLogCommand(NotarizationCredentials credentials, String appleRequestUUID) {
        return null;
    }
}
