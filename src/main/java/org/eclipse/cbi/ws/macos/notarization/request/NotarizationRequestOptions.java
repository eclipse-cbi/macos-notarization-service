/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.request;

import com.google.auto.value.AutoValue;
import com.squareup.moshi.JsonClass;

@JsonClass(generateAdapter = true, generator = "avm")
@AutoValue
public abstract class NotarizationRequestOptions {
	public abstract String primaryBundleId();

	public abstract boolean staple();

	static NotarizationRequestOptions.Builder builder() {
		return new AutoValue_NotarizationRequestOptions.Builder().staple(false);
	}

	@AutoValue.Builder
	abstract static class Builder {
		abstract NotarizationRequestOptions.Builder primaryBundleId(String primaryBundleId);

		abstract NotarizationRequestOptions.Builder staple(boolean staple);

		abstract NotarizationRequestOptions build();
	}
}