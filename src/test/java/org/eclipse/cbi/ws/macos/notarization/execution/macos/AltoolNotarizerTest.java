/*******************************************************************************
 * Copyright (c) 2023 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.execution.macos;

import okhttp3.OkHttpClient;
import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;
import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizationInfoResult;
import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizationInfoResultBuilder;
import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

public class AltoolNotarizerTest {

    private AltoolNotarizer tool;

    @BeforeEach
    public void setup() {
        tool = new AltoolNotarizer(new OkHttpClient.Builder().build());
    }

    @Test
    public void analyzeSuccessfulSubmission() throws ExecutionException {
        Path stdout = Path.of(this.getClass().getResource("altool/submission-success.log").getPath());
        Path stderr = Path.of("non-existing");

        NativeProcess.Result r =
            NativeProcess.Result.builder()
                .exitValue(0)
                .arg0("")
                .stdout(stdout)
                .stderr(stderr)
                .build();

        NotarizerResult result = tool.analyzeSubmissionResult(r, Path.of("Alfred_5.1.2_2145.dmg"));

        assertEquals(NotarizerResult.Status.UPLOAD_SUCCESSFUL, result.status());
        assertEquals("a518bb0a-fdaa-4f73-aa09-c7a9b699ac59", result.appleRequestUUID());
        assertEquals("No errors uploading 'Alfred_5.1.2_2145.dmg'.", result.message());
    }

    @Test
    public void analyzeSubmissionInProgress() throws ExecutionException {
        Path stdout = Path.of(this.getClass().getResource("altool/submission-in-progress.log").getPath());
        Path stderr = Path.of("non-existing");

        NativeProcess.Result r =
            NativeProcess.Result.builder()
                .exitValue(176)
                .arg0("")
                .stdout(stdout)
                .stderr(stderr)
                .build();

        NotarizerResult result = tool.analyzeSubmissionResult(r, Path.of("Alfred_5.1.2_2145.dmg"));

        assertEquals(NotarizerResult.Status.UPLOAD_SUCCESSFUL, result.status());
        assertEquals("a518bb0a-fdaa-4f73-aa09-c7a9b699ac59", result.appleRequestUUID());
        assertEquals("Notarization in progress (software asset has been already previously uploaded to Apple notarization service)", result.message());
    }

    @Test
    public void analyzeInfoSuccess() throws ExecutionException {
        Path stdout = Path.of(this.getClass().getResource("altool/info-success.log").getPath());
        Path stderr = Path.of("non-existing");

        NativeProcess.Result r =
            NativeProcess.Result.builder()
                .exitValue(0)
                .arg0("")
                .stdout(stdout)
                .stderr(stderr)
                .build();

        // Consider using a mock HttpClient for retrieving the log
        NotarizationInfoResultBuilder resultBuilder = NotarizationInfoResult.builder();
        new AltoolNotarizer(null).analyzeInfoResult(r, resultBuilder, "a518bb0a-fdaa-4f73-aa09-c7a9b699ac59");
        NotarizationInfoResult result = resultBuilder.build();

        assertEquals(NotarizationInfoResult.Status.NOTARIZATION_SUCCESSFUL, result.status());
        assertEquals("Notarization status: Package Approved", result.message());
        assertNull(result.notarizationLog());
    }
}
