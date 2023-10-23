/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.common;

import io.soabase.recordbuilder.core.RecordBuilder;

import javax.annotation.Nullable;

@RecordBuilder
public record NotarizerResult(Status status, String message, @Nullable String appleRequestUUID) {
	public enum Status { UPLOAD_SUCCESSFUL, UPLOAD_FAILED }

	public static NotarizerResultBuilder builder() {
		return NotarizerResultBuilder.builder();
	}
}