/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.request;

import io.soabase.recordbuilder.core.RecordBuilder;

import java.util.UUID;

@RecordBuilder
public record NotarizationStatusWithUUID(UUID uuid, NotarizationStatus notarizationStatus) {
	public static NotarizationStatusWithUUIDBuilder builder() {
		return NotarizationStatusWithUUIDBuilder.builder();
	}

	public static NotarizationStatusWithUUID from(UUID uuid, NotarizationStatus status) {
		return new NotarizationStatusWithUUID(uuid, status);
	}
}
