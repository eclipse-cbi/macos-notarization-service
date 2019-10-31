/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.altool;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class StaplerResult {
	public enum Status {ERROR, SUCCESS}
	public abstract StaplerResult.Status status();
	public abstract String message();
}