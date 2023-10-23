/*******************************************************************************
 * Copyright (c) 2023 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.eclipse.cbi.ws.macos.notarization.request.NotarizationRequestOptions;
import org.eclipse.cbi.ws.macos.notarization.request.NotarizationStatus;
import org.eclipse.cbi.ws.macos.notarization.request.NotarizationStatusWithUUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.UUID;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class NotarizationServiceTest {

    @BeforeAll
    public static void setup() {
        // For debugging only
        // RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }

    @Test
    public void testNotarizationFailure() throws InterruptedException {
        // install a different notarization tool for this test, that fails all
        // the time when notarizing a file.
        QuarkusMock.installMockForType(new FailingTestProducer(), Producer.class);

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
            System.out.println(status);
            if (status.notarizationStatus().status() != NotarizationStatus.State.IN_PROGRESS) {
                break;
            }

            Thread.sleep(1000);
        }

        assertEquals(NotarizationStatus.State.ERROR, status.notarizationStatus().status());
    }

    @Test
    public void testNotarizationSuccess() throws InterruptedException {
        // install a different notarization tool for this test, that fails all
        // the time when notarizing a file.
        QuarkusMock.installMockForType(new PassingTestProducer(), Producer.class);

        NotarizationRequestOptions options =
                NotarizationRequestOptions
                        .builder()
                        .primaryBundleId("1234")
                        .staple(false)
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

        extract =
            get("/macos-notarization-service/%1$s/status".formatted(status.uuid()))
                .then()
                .statusCode(200)
                .extract();

        status = extract.body().as(NotarizationStatusWithUUID.class);
        System.out.println(status);
        assertEquals(NotarizationStatus.State.COMPLETE, status.notarizationStatus().status());
    }
}