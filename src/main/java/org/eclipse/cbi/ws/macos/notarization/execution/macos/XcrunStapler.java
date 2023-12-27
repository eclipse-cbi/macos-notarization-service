/*******************************************************************************
 * Copyright (c) 2023 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.execution.macos;

import com.google.common.collect.ImmutableList;
import org.eclipse.cbi.ws.macos.notarization.execution.StaplerTool;

import java.nio.file.Path;
import java.util.List;

public class XcrunStapler extends StaplerTool {
    @Override
    protected List<String> getStapleCommand(Path fileToStaple) {
        return ImmutableList.<String>builder()
                .add("xcrun", "stapler")
                .add("staple", fileToStaple.toString())
                .build();
    }
}
