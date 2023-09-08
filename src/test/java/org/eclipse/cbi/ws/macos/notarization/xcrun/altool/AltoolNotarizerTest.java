/*******************************************************************************
 * Copyright (c) 2023 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.altool;

import org.eclipse.cbi.ws.macos.notarization.process.NativeProcess;
import org.eclipse.cbi.ws.macos.notarization.xcrun.common.NotarizationInfoResult;
import org.eclipse.cbi.ws.macos.notarization.xcrun.common.NotarizerResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

public class AltoolNotarizerTest {

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

        NotarizerResult result = new AltoolNotarizer().analyzeSubmissionResult(r, Path.of("Alfred_5.1.2_2145.dmg"));

        assertEquals(NotarizerResult.Status.UPLOAD_SUCCESSFUL, result.status());
        assertEquals("a518bb0a-fdaa-4f73-aa09-c7a9b699ac59", result.appleRequestUUID());
        assertEquals("No errors uploading 'Alfred_5.1.2_2145.dmg'.", result.message());
    }

    @Test
    public void analyzeSubmissionInProgress() throws ExecutionException {
        Path stdout = Path.of(this.getClass().getResource("submission-in-progress.log").getPath());
        Path stderr = Path.of("non-existing");

        NativeProcess.Result r =
            NativeProcess.Result.builder()
                .exitValue(176)
                .arg0("")
                .stdout(stdout)
                .stderr(stderr)
                .build();

        NotarizerResult result = new AltoolNotarizer().analyzeSubmissionResult(r, Path.of("Alfred_5.1.2_2145.dmg"));

        assertEquals(NotarizerResult.Status.UPLOAD_SUCCESSFUL, result.status());
        assertEquals("a518bb0a-fdaa-4f73-aa09-c7a9b699ac59", result.appleRequestUUID());
        assertEquals("Notarization in progress (software asset has been already previously uploaded to Apple notarization service)", result.message());
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

        // Consider using a mock HttpClient for retrieving the log
        NotarizationInfoResult result = new AltoolNotarizer().analyzeInfoResult(r, "a518bb0a-fdaa-4f73-aa09-c7a9b699ac59", null);

        assertEquals(NotarizationInfoResult.Status.NOTARIZATION_SUCCESSFUL, result.status());
        assertEquals("Notarization status: Package Approved", result.message());
        assertEquals("Can not retrieve log, httpClient is null", result.notarizationLog());
    }

}
