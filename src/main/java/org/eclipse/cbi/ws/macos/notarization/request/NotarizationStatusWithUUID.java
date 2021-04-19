/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.request;

import java.util.UUID;

import com.google.auto.value.AutoValue;
import com.squareup.moshi.JsonClass;

@JsonClass(generateAdapter = true, generator = "avm")
@AutoValue
public abstract class NotarizationStatusWithUUID {

	public abstract UUID uuid();
	
	public abstract NotarizationStatus notarizationStatus();
	
	public static NotarizationStatusWithUUID from(UUID uuid, NotarizationStatus status) {
		return new AutoValue_NotarizationStatusWithUUID(uuid, status);
	}
}
