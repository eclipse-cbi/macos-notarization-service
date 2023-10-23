/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.request;

import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public record NotarizationStatus(NotarizationStatus.State status, String message, String moreInfo, String log) {
	public enum State { COMPLETE, IN_PROGRESS, ERROR };

	public static NotarizationStatusBuilder builder() {
		return NotarizationStatusBuilder.builder();
	}
}