/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.execution.result;

import io.soabase.recordbuilder.core.RecordBuilder;

import javax.annotation.Nullable;

@RecordBuilder
public record NotarizationInfoResult(Status status, String message, @Nullable String notarizationLog) {
	public enum Status {NOTARIZATION_IN_PROGRESS, NOTARIZATION_FAILED, NOTARIZATION_SUCCESSFUL, RETRIEVAL_FAILED}

	public static NotarizationInfoResultBuilder builder() {
		return NotarizationInfoResultBuilder.builder();
	}
}
