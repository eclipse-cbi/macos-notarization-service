/*******************************************************************************
 * Copyright (c) 2023 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.eclipse.cbi.ws.macos.notarization.execution.NotarizationCredentials;
import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizationInfoResultBuilder;
import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;
import org.eclipse.cbi.ws.macos.notarization.request.NotarizationRequestOptions;
import org.eclipse.cbi.ws.macos.notarization.request.NotarizationStatus;
import org.eclipse.cbi.ws.macos.notarization.request.NotarizationStatusWithUUID;
import org.eclipse.cbi.ws.macos.notarization.execution.NotarizationTool;
import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizerResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
public class FailingNotarizationServiceTest {

    @Inject
    NotarizationService service;

    @BeforeAll
    public static void setup() {
        // For debugging only
        // RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }

    @Test
    public void notarizationFailure() throws InterruptedException {
        // use a different notarization tool for this test, that fails all
        // the time when notarizing a file.
        service.notarizationTool = new FailingNotarizationTool();

        NotarizationRequestOptions options =
                NotarizationRequestOptions
                        .builder()
                        .primaryBundleId("1234")
                        .staple(true)
                        .build();

        ExtractableResponse<Response> extract = given()
                .when()
                .multiPart("file", Paths.get("pom.xml").toFile())
                .multiPart("options", options, "application/json")
                .post("/macos-notarization-service/notarize")
                .then()
                .statusCode(200).extract();

        NotarizationStatusWithUUID status = extract.body().as(NotarizationStatusWithUUID.class);
        UUID uuid = status.uuid();
        assertNotNull(uuid);
        assertEquals(NotarizationStatus.State.IN_PROGRESS, status.notarizationStatus().status());

        for (int attempt = 1; attempt < 10; attempt++) {
            extract =
                    get("/macos-notarization-service/%1$s/status".formatted(status.uuid()))
                            .then()
                            .statusCode(200)
                            .extract();

            status = extract.body().as(NotarizationStatusWithUUID.class);
            if (status.notarizationStatus().status() != NotarizationStatus.State.IN_PROGRESS) {
                break;
            }

            Thread.sleep(1000);
        }

        assertEquals(NotarizationStatus.State.ERROR, status.notarizationStatus().status());
    }

    static class FailingNotarizationTool extends NotarizationTool {

        @Override
        protected List<String> getUploadCommand(NotarizationCredentials credentials, String primaryBundleId, Path fileToNotarize) {
            return null;
        }

        @Override
        protected NotarizerResult analyzeSubmissionResult(NativeProcess.Result nativeProcessResult, Path fileToNotarize) {
            return null;
        }

        @Override
        protected List<String> getInfoCommand(NotarizationCredentials credentials, String appleRequestUUID) {
            return null;
        }

        @Override
        protected boolean analyzeInfoResult(NativeProcess.Result nativeProcessResult, NotarizationInfoResultBuilder resultBuilder, String appleRequestUUID) {
            return false;
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
}