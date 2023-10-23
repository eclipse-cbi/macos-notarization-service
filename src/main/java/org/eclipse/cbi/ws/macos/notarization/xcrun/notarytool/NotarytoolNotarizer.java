/*******************************************************************************
 * Copyright (c) 2023 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.notarytool;

import com.google.common.collect.ImmutableList;
import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;
import org.eclipse.cbi.ws.macos.notarization.xcrun.common.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class NotarytoolNotarizer extends NotarizationTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotarytoolNotarizer.class);

    @Override
    protected List<String> getUploadCommand(String appleIDUsername, String appleIDPassword, String appleIDTeamID, String primaryBundleId, Path fileToNotarize) {
        return ImmutableList.<String>builder()
            .add("xcrun", "notarytool")
            .add("submit")
            .add("--output-format", "plist")
            .add("--apple-id", appleIDUsername)
            .add("--password", appleIDPassword)
            .add("--team-id", appleIDTeamID)
            .add(fileToNotarize.toString()).build();
    }

    @Override
    protected NotarizerResult analyzeSubmissionResult(NativeProcess.Result nativeProcessResult, Path fileToNotarize) throws ExecutionException {
        NotarizerResultBuilder resultBuilder = NotarizerResult.builder();
        try {
            PListDict plist = PListDict.fromXML(nativeProcessResult.stdoutAsStream());
            if (nativeProcessResult.exitValue() == 0) {
                String requestUUID = (String) plist.get("id");
                if (requestUUID != null) {
                    resultBuilder
                        .status(NotarizerResult.Status.UPLOAD_SUCCESSFUL)
                        .message((String) plist.get("message"))
                        .appleRequestUUID(requestUUID);
                } else {
                    throw new IllegalStateException("Cannot find the Apple request ID from response " + plist);
                }
            } else {
                Optional<String> rawErrorMessage = plist.messageFromFirstProductError();
                if (rawErrorMessage.isPresent()) {
                    resultBuilder
                        .status(NotarizerResult.Status.UPLOAD_FAILED)
                        .message("Failed to notarize the requested file. Reason: " + rawErrorMessage.get());
                } else {
                    resultBuilder
                        .status(NotarizerResult.Status.UPLOAD_FAILED)
                        .message("Failed to notarize the requested file. Reason: xcrun notarytool exit value was " +
                                 nativeProcessResult.exitValue() +
                                 " with no parsable error message. See server log for more details.");
                }
            }
        } catch (IOException | SAXException e) {
            LOGGER.error("Error while parsing the output after the upload of '" + fileToNotarize + "' to the Apple notarization service", e);
            throw new ExecutionException("Error while parsing the output after the upload of the file to be notarized", e);
        }
        return resultBuilder.build();
    }

    @Override
    protected List<String> getInfoCommand(String appleIDUsername, String appleIDPassword, String appleIDTeamID, String appleRequestUUID) {
        return ImmutableList.<String>builder().add("xcrun", "notarytool")
            .add("info")
            .add("--output-format", "plist")
            .add("--apple-id", appleIDUsername)
            .add("--password", appleIDPassword)
            .add("--team-id", appleIDTeamID)
            .add(appleRequestUUID)
            .build();
    }

    @Override
    protected boolean analyzeInfoResult(NativeProcess.Result nativeProcessResult,
                                        NotarizationInfoResultBuilder resultBuilder,
                                        String appleRequestUUID) throws ExecutionException {
        try {
            PListDict plist = PListDict.fromXML(nativeProcessResult.stdoutAsStream());
            if (nativeProcessResult.exitValue() == 0) {
                return parseNotarizationInfo(plist, resultBuilder);
            } else {
                resultBuilder
                    .status(NotarizationInfoResult.Status.RETRIEVAL_FAILED)
                    .message((String) plist.get("message"));
                return false;
            }
        } catch (IOException | SAXException e) {
            LOGGER.error("Cannot parse notarization info for request '" + appleRequestUUID + "'", e);
            throw new ExecutionException("Failed to retrieve notarization info.", e);
        }
    }

    private boolean parseNotarizationInfo(PListDict plist,
                                          NotarizationInfoResultBuilder resultBuilder) {
        Object status = plist.get("status");
        if (status instanceof String statusStr) {
            if ("accepted".equalsIgnoreCase(statusStr)) {
                resultBuilder
                    .status(NotarizationInfoResult.Status.NOTARIZATION_SUCCESSFUL)
                    .message("Notarization status: " + plist.get("message"));
                return true;
            } else if ("in progress".equalsIgnoreCase(statusStr)) {
                resultBuilder
                    .status(NotarizationInfoResult.Status.NOTARIZATION_IN_PROGRESS)
                    .message("Notarization in progress");
            } else {
                resultBuilder.status(NotarizationInfoResult.Status.NOTARIZATION_FAILED);

                Optional<String> errorMessage = plist.messageFromFirstProductError();
                OptionalInt errorCode = plist.firstProductErrorCode();
                resultBuilder.message("Failed to notarize the requested file (status="+statusStr+"). Error code="+errorCode+". Reason: " + errorMessage);
                return true;
            }
        } else {
            throw new IllegalStateException("Cannot parse 'status'.");
        }

        return false;
    }

    @Override
    protected boolean hasLogCommand() {
        return true;
    }

    @Override
    protected List<String> getLogCommand(String appleIDUsername, String appleIDPassword, String appleIDTeamID, String appleRequestUUID) {
        return ImmutableList.<String>builder().add("xcrun", "notarytool")
                .add("log")
                .add("--apple-id", appleIDUsername)
                .add("--password", appleIDPassword)
                .add("--team-id", appleIDTeamID)
                .add(appleRequestUUID)
                .build();
    }
}
