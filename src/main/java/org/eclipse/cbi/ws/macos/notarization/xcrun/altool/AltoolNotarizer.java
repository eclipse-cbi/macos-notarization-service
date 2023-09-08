/*******************************************************************************
 * Copyright (c) 2023 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.altool;

import com.google.common.collect.ImmutableList;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;
import org.eclipse.cbi.ws.macos.notarization.xcrun.common.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AltoolNotarizer extends NotarizationTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(AltoolNotarizer.class);

    private static final Pattern UPLOADID_PATTERN = Pattern.compile(".*The upload ID is ([A-Za-z0-9\\\\-]*).*");
    private static final Pattern ERROR_MESSAGE_PATTERN = Pattern.compile(".*\"(.*)\".*");


    @Override
    protected List<String> getUploadCommand(String appleIDUsername, String primaryBundleId, Path fileToNotarize) {
        List<String> cmd = ImmutableList.<String>builder()
            .add("xcrun", "altool")
            .add("--notarize-app")
            .add("--output-format", "xml")
            .add("--username", appleIDUsername)
            .add("--password", "@env:" + APPLEID_PASSWORD_ENV_VAR_NAME)
            .add("--primary-bundle-id", primaryBundleId)
            .add("--file", fileToNotarize.toString()).build();

        return cmd;
    }

    protected NotarizerResult analyzeSubmissionResult(NativeProcess.Result nativeProcessResult,
                                                      Path fileToNotarize) throws ExecutionException {

        NotarizerResult.Builder resultBuilder = NotarizerResult.builder();
        try {
            PListDict plist = PListDict.fromXML(nativeProcessResult.stdoutAsStream());
            if (nativeProcessResult.exitValue() == 0) {
                Optional<String> requestUUID = plist.requestUUIDFromNotarizationUpload();
                if (requestUUID.isPresent()) {
                    resultBuilder
                            .status(NotarizerResult.Status.UPLOAD_SUCCESSFUL)
                            .message((String) plist.get("success-message"))
                            .appleRequestUUID(requestUUID.get());
                } else {
                    throw new IllegalStateException("Cannot find the Apple request ID from response " + plist);
                }
            } else {
                Optional<String> rawErrorMessage = plist.messageFromFirstProductError();
                if (rawErrorMessage.isPresent()) {
                    analyzeErrorMessage(rawErrorMessage.get(), resultBuilder);
                } else {
                    resultBuilder
                            .status(NotarizerResult.Status.UPLOAD_FAILED)
                            .message("Failed to notarize the requested file. Reason: xcrun altool exit value was " +
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

    private void analyzeErrorMessage(String errorMessage, NotarizerResult.Builder resultBuilder) {
        if (errorMessage.contains("ITMS-4302")) {
            // ERROR ITMS-4302: "The software asset has an invalid primary bundle identifier '{}'"
            errorITMS4302(errorMessage, resultBuilder);
        } else if (errorMessage.contains("ITMS-90732")) {
            // ERROR ITMS-90732: "The software asset has already been uploaded. The upload ID is {}"
            errorITMS90732(errorMessage, resultBuilder);
        } else {
            resultBuilder.status(NotarizerResult.Status.UPLOAD_FAILED)
                    .message("Failed to notarize the requested file. Reason: " + errorMessage);
        }
    }

    private void errorITMS90732(String errorMessage, NotarizerResult.Builder resultBuilder) {
        Optional<String> appleRequestID = parseAppleRequestID(errorMessage);
        if (appleRequestID.isPresent()) {
            resultBuilder.status(NotarizerResult.Status.UPLOAD_SUCCESSFUL)
                    .message("Notarization in progress (software asset has been already previously uploaded to Apple notarization service)")
                    .appleRequestUUID(appleRequestID.get());
        } else {
            throw new IllegalStateException("Cannot parse the Apple request ID from error message while error is ITMS-90732");
        }
    }

    private void errorITMS4302(String errorMessage, NotarizerResult.Builder resultBuilder) {
        resultBuilder.status(NotarizerResult.Status.UPLOAD_FAILED);
        Matcher matcher = ERROR_MESSAGE_PATTERN.matcher(errorMessage);
        if (matcher.matches()) {
            resultBuilder.message(matcher.group(1));
        } else {
            resultBuilder.message("The software asset has an invalid primary bundle identifier");
        }
    }

    private Optional<String> parseAppleRequestID(String message) {
        try {
            Matcher matcher = UPLOADID_PATTERN.matcher(message);
            if (matcher.matches()) {
                return Optional.of(matcher.group(1));
            }
        } catch (IllegalStateException e) {
            LOGGER.info("No Apple request ID in xcrun output", e);
        }
        return Optional.empty();
    }

    @Override
    protected List<String> getInfoCommand(String appleIDUsername, String appleRequestUUID) {
        List<String> cmd = ImmutableList.<String>builder().add("xcrun", "altool")
            .add("--notarization-info", appleRequestUUID.toString())
            .add("--output-format", "xml")
            .add("--username", appleIDUsername)
            .add("--password", "@env:" + APPLEID_PASSWORD_ENV_VAR_NAME)
            .build();

        return cmd;
    }

    @Override
    protected NotarizationInfoResult analyzeInfoResult(NativeProcess.Result nativeProcessResult,
                                                       String appleRequestUUID,
                                                       OkHttpClient httpClient) throws ExecutionException {

        NotarizationInfoResult.Builder resultBuilder = NotarizationInfoResult.builder();
        try {
            PListDict plist = PListDict.fromXML(nativeProcessResult.stdoutAsStream());

            if (nativeProcessResult.exitValue() == 0) {
                Map<?, ?> notarizationInfoList = (Map<?, ?>) plist.get("notarization-info");
                if (notarizationInfoList != null && !notarizationInfoList.isEmpty()) {
                    parseNotarizationInfo(plist, notarizationInfoList, resultBuilder, httpClient);
                } else {
                    LOGGER.error("Error while parsing notarization info plist file. Cannot find 'notarization-info' section");
                    resultBuilder.status(NotarizationInfoResult.Status.RETRIEVAL_FAILED)
                            .message("Error while parsing notarization info plist file. Cannot find 'notarization-info' section");
                }
            } else {
                resultBuilder.status(NotarizationInfoResult.Status.RETRIEVAL_FAILED);
                OptionalInt firstProductErrorCode = plist.firstProductErrorCode();
                if (firstProductErrorCode.isPresent()) {
                    switch (firstProductErrorCode.getAsInt()) {
                        case 1519: // Could not find the RequestUUID.
                            resultBuilder.message("Error while retrieving notarization info from Apple service. Remote service could not find the RequestUUID");
                            break;
                        case -18000: // ERROR ITMS-90732: "The software asset has already been uploaded.
                            resultBuilder
                                    .status(NotarizationInfoResult.Status.NOTARIZATION_IN_PROGRESS)
                                    .message("The software asset has already been uploaded. Notarization in progress");
                        default:
                            resultBuilder.message("Failed to notarize the requested file. Remote service error code = " + firstProductErrorCode.getAsInt() + " (xcrun altool exit value ="+nativeProcessResult.exitValue()+").");
                            break;
                    }
                } else {
                    Optional<String> errorMessage = plist.messageFromFirstProductError();
                    if (errorMessage.isPresent()) {
                        resultBuilder.message("Failed to notarize the requested file (xcrun altool exit value ="+nativeProcessResult.exitValue()+"). Reason: " + errorMessage.get());
                    } else {
                        resultBuilder.message("Failed to notarize the requested file (xcrun altool exit value ="+nativeProcessResult.exitValue()+").");
                    }
                }
            }
        } catch (IOException | SAXException e) {
            LOGGER.error("Cannot parse notarization info for request '" + appleRequestUUID + "'", e);
            throw new ExecutionException("Failed to retrieve notarization info.", e);
        }
        return resultBuilder.build();
    }

    private void parseNotarizationInfo(PListDict plist, Map<?, ?> notarizationInfo,
                                       NotarizationInfoResult.Builder resultBuilder,
                                       OkHttpClient httpClient) {
        Object status = notarizationInfo.get("Status");
        if (status instanceof String) {
            String statusStr = (String) status;
            if ("success".equalsIgnoreCase(statusStr)) {
                resultBuilder
                        .status(NotarizationInfoResult.Status.NOTARIZATION_SUCCESSFUL)
                        .message("Notarization status: " + notarizationInfo.get("Status Message"))
                        .notarizationLog(extractLogFromServer(notarizationInfo, httpClient));
            } else if ("in progress".equalsIgnoreCase(statusStr)) {
                resultBuilder
                        .status(NotarizationInfoResult.Status.NOTARIZATION_IN_PROGRESS)
                        .message("Notarization in progress");
            } else {
                resultBuilder
                        .status(NotarizationInfoResult.Status.NOTARIZATION_FAILED)
                        .notarizationLog(extractLogFromServer(notarizationInfo, httpClient));

                Optional<String> errorMessage = plist.messageFromFirstProductError();
                OptionalInt errorCode = plist.firstProductErrorCode();
                resultBuilder.message("Failed to notarize the requested file (status="+statusStr+"). Error code="+errorCode+". Reason: " + errorMessage);
            }
        } else {
            throw new IllegalStateException("Cannot parse 'Status' from notarization-info");
        }
    }

    private String extractLogFromServer(Map<?, ?> notarizationInfo, OkHttpClient httpClient) {
        if (httpClient == null) {
            return "Can not retrieve log, httpClient is null";
        }

        Object logFileUrlStr = notarizationInfo.get("LogFileURL");
        if (logFileUrlStr instanceof String) {
            HttpUrl logfileUrl = HttpUrl.parse((String)logFileUrlStr);
            if (logfileUrl != null) {
                return logFromServer(logfileUrl, httpClient);
            } else {
                return "LogFileURL from plist file is invalid '"+logFileUrlStr+"'";
            }
        } else {
            return "Unable to find LogFileURL in parsed plist file";
        }
    }

    private String logFromServer(HttpUrl logFileUrl, OkHttpClient httpClient) {
        try {
            RetryPolicy<String> retryPolicy = new RetryPolicy<String>().withDelay(Duration.ofSeconds(10));
            return Failsafe.with(retryPolicy).get(() ->
                    httpClient.newCall(new Request.Builder().url(logFileUrl).build()).execute().body().string());
        } catch (FailsafeException e) {
            LOGGER.error("Error while retrieving log from Apple server (logFileURL= "+logFileUrl+" )", e.getCause());
            return "Error while retrieving log from Apple server";
        }
    }
}
