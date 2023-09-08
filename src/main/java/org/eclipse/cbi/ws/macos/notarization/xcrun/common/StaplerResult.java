/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.common;

import java.util.Collection;


public interface StaplerResult {
	enum Status {ERROR, SUCCESS}
	StaplerResult.Status status();
	String message();

	static StaplerResult from(Collection<? extends StaplerResult> results) {
		return new ComposedStaplerResult(results);
	}
}