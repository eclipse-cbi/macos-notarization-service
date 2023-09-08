package org.eclipse.cbi.ws.macos.notarization.xcrun.notarytool;

import com.google.common.collect.ImmutableList;
import okhttp3.OkHttpClient;
import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;
import org.eclipse.cbi.ws.macos.notarization.xcrun.common.NotarizationInfoResult;
import org.eclipse.cbi.ws.macos.notarization.xcrun.common.NotarizationTool;
import org.eclipse.cbi.ws.macos.notarization.xcrun.common.NotarizerResult;
import org.eclipse.cbi.ws.macos.notarization.xcrun.common.PListDict;
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
    protected List<String> getUploadCommand(String appleIDUsername, String appleIDTeamID, String primaryBundleId, Path fileToNotarize) {
        List<String> cmd =
            ImmutableList.<String>builder()
                .add("xcrun", "notarytool")
                .add("submit")
                .add("--output-format", "plist")
                .add("--apple-id", appleIDUsername)
                .add("--team-id", appleIDTeamID)
                .add("--password", "@env:" + APPLEID_PASSWORD_ENV_VAR_NAME)
                .add(fileToNotarize.toString()).build();

        return cmd;
    }

    @Override
    protected NotarizerResult analyzeSubmissionResult(NativeProcess.Result nativeProcessResult, Path fileToNotarize) throws ExecutionException {
        NotarizerResult.Builder resultBuilder = NotarizerResult.builder();
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
    protected List<String> getInfoCommand(String appleIDUsername, String appleIDTeamID, String appleRequestUUID) {
        List<String> cmd =
            ImmutableList.<String>builder().add("xcrun", "notarytool")
                .add("info")
                .add("--output-format", "plist")
                .add("--apple-id", appleIDUsername)
                .add("--team-id", appleIDTeamID)
                .add("--password", "@env:" + APPLEID_PASSWORD_ENV_VAR_NAME)
                .add(appleRequestUUID)
                .build();

        return cmd;
    }

    @Override
    protected boolean analyzeInfoResult(NativeProcess.Result nativeProcessResult,
                                        NotarizationInfoResult.Builder resultBuilder,
                                        String appleRequestUUID,
                                        OkHttpClient httpClient) throws ExecutionException {
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
                                          NotarizationInfoResult.Builder resultBuilder) {
        Object status = plist.get("status");
        if (status instanceof String) {
            String statusStr = (String) status;
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
    protected List<String> getLogCommand(String appleIDUsername, String appleIDTeamID, String appleRequestUUID) {
        List<String> cmd =
                ImmutableList.<String>builder().add("xcrun", "notarytool")
                        .add("log")
                        .add("--apple-id", appleIDUsername)
                        .add("--team-id", appleIDTeamID)
                        .add("--password", "@env:" + APPLEID_PASSWORD_ENV_VAR_NAME)
                        .add(appleRequestUUID)
                        .build();

        return cmd;
    }
}
