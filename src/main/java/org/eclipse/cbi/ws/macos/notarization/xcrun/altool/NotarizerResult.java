/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.altool;

import javax.annotation.Nullable;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class NotarizerResult {
	
	public enum Status { UPLOAD_SUCCESSFUL, UPLOAD_FAILED }
	public abstract NotarizerResult.Status status();
	public abstract String message();
	
	@Nullable
	public abstract String appleRequestUUID();

	static NotarizerResult.Builder builder() {
		return new AutoValue_NotarizerResult.Builder();
	}

	@AutoValue.Builder
	static abstract class Builder {
		abstract NotarizerResult.Builder status(NotarizerResult.Status status);
		abstract NotarizerResult.Builder message(String message);
		abstract NotarizerResult.Builder appleRequestUUID(String requestUUID);
		abstract NotarizerResult build();
	}
}