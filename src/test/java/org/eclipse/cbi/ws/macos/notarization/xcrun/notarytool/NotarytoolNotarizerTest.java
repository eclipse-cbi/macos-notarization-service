/*******************************************************************************
 * Copyright (c) 2023 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.notarytool;

import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;
import org.eclipse.cbi.ws.macos.notarization.xcrun.common.NotarizationInfoResult;
import org.eclipse.cbi.ws.macos.notarization.xcrun.common.NotarizationInfoResultBuilder;
import org.eclipse.cbi.ws.macos.notarization.xcrun.common.NotarizerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class NotarytoolNotarizerTest {

    private NotarytoolNotarizer tool;

    @BeforeEach
    public void setup() {
        tool = new NotarytoolNotarizer();
    }

    @Test
    public void analyzeSuccessfulSubmission() throws ExecutionException {
        Path stdout = Path.of(this.getClass().getResource("submission-success.log").getPath());
        Path stderr = Path.of("non-existing");

        NativeProcess.Result r =
            NativeProcess.Result.builder()
                .exitValue(0)
                .arg0("")
                .stdout(stdout)
                .stderr(stderr)
                .build();

        NotarizerResult result = tool.analyzeSubmissionResult(r, Path.of("SuperDuper.dmg"));

        assertEquals(NotarizerResult.Status.UPLOAD_SUCCESSFUL, result.status());
        assertEquals("ac9a4320-49c8-453c-82a3-996d83bd20f5", result.appleRequestUUID());
        assertEquals("Successfully uploaded file", result.message());
    }

    @Test
    public void analyzeInfoInProgress() throws ExecutionException {
        Path stdout = Path.of(this.getClass().getResource("info-in-progress.log").getPath());
        Path stderr = Path.of("non-existing");

        NativeProcess.Result r =
            NativeProcess.Result.builder()
                .exitValue(0)
                .arg0("")
                .stdout(stdout)
                .stderr(stderr)
                .build();

        NotarizationInfoResultBuilder resultBuilder = NotarizationInfoResult.builder();
        tool.analyzeInfoResult(r, resultBuilder, "a518bb0a-fdaa-4f73-aa09-c7a9b699ac59");
        NotarizationInfoResult result = resultBuilder.build();

        assertEquals(NotarizationInfoResult.Status.NOTARIZATION_IN_PROGRESS, result.status());
        assertEquals("Notarization in progress", result.message());
        assertNull(result.notarizationLog());
    }

    @Test
    public void analyzeInfoSuccess() throws ExecutionException {
        Path stdout = Path.of(this.getClass().getResource("info-success.log").getPath());
        Path stderr = Path.of("non-existing");

        NativeProcess.Result r =
                NativeProcess.Result.builder()
                        .exitValue(0)
                        .arg0("")
                        .stdout(stdout)
                        .stderr(stderr)
                        .build();

        NotarizationInfoResultBuilder resultBuilder = NotarizationInfoResult.builder();
        tool.analyzeInfoResult(r, resultBuilder, "a518bb0a-fdaa-4f73-aa09-c7a9b699ac59");
        NotarizationInfoResult result = resultBuilder.build();

        assertEquals(NotarizationInfoResult.Status.NOTARIZATION_SUCCESSFUL, result.status());
        assertEquals("Notarization status: Successfully received submission info", result.message());
        assertNull(result.notarizationLog());
    }
}
