/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.request;

import javax.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.squareup.moshi.JsonClass;

@JsonClass(generateAdapter = true, generator = "avm")
@AutoValue
public abstract class NotarizationStatus {
	public enum State { COMPLETE, IN_PROGRESS, ERROR };
	public abstract NotarizationStatus.State status();

	public abstract String message();

	@Nullable
	public abstract String moreInfo();

	@Nullable
	public abstract String log();

	abstract NotarizationStatus.Builder toBuilder();

	static NotarizationStatus.Builder builder() {
		return new AutoValue_NotarizationStatus.Builder();
	}

	@AutoValue.Builder
	abstract static class Builder {
		abstract NotarizationStatus.Builder status(NotarizationStatus.State status);

		abstract NotarizationStatus.Builder message(String message);

		abstract NotarizationStatus.Builder moreInfo(String moreInfo);

		abstract NotarizationStatus.Builder log(String log);

		abstract NotarizationStatus build();
	}

}