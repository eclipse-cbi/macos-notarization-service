/*******************************************************************************
 * Copyright (c) 2023 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.common;

import okhttp3.OkHttpClient;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

public interface NotarizationTool {
    NotarizerResult upload(String appleIDUsername,
                           String appleIDPassword,
                           String primaryBundleId,
                           Path fileToNotarize,
                           Duration uploadTimeout) throws ExecutionException, IOException;

    NotarizationInfoResult retrieveInfo(String appleIDUsername,
                                        String appleIDPassword,
                                        String appleRequestUUID,
                                        Duration pollingTimeout,
                                        OkHttpClient httpClient) throws ExecutionException, IOException;
}