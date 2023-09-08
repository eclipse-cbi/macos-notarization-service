/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.common;

import javax.annotation.Nullable;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class NotarizationInfoResult {
	public enum Status {NOTARIZATION_IN_PROGRESS, NOTARIZATION_FAILED, NOTARIZATION_SUCCESSFUL, RETRIEVAL_FAILED}
	public abstract Status status();
	public abstract String message();
	@Nullable
	public abstract String notarizationLog();

	public static NotarizationInfoResult.Builder builder() {
		return new AutoValue_NotarizationInfoResult.Builder();
	}

	@AutoValue.Builder
	public static abstract class Builder {
		public abstract NotarizationInfoResult.Builder status(Status status);
		public abstract NotarizationInfoResult.Builder message(String message);
		public abstract NotarizationInfoResult.Builder notarizationLog(String notarizationLog);
		public abstract NotarizationInfoResult build();
	}
}