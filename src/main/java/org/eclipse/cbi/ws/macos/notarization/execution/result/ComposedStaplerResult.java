/*******************************************************************************
 * Copyright (c) 2020 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.execution.result;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ComposedStaplerResult implements StaplerResult {
    private final List<StaplerResult> results;

    ComposedStaplerResult(Collection<? extends StaplerResult> results) {
        this.results = new ArrayList<>(results);
    }

    @Override
    public Status status() {
        if (results.stream().anyMatch(r -> r.status() == Status.ERROR)) {
            return Status.ERROR;
        }
        return Status.SUCCESS;
    }

    @Override
    public String message() {
        return results.stream()
                .map(StaplerResult::message)
                .collect(Collectors.joining("\n"));
    }
}
