/*******************************************************************************
 * Copyright (c) 2023 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.execution;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.cbi.ws.macos.notarization.NotarizationService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Startup
@ApplicationScoped
public class NotarizationCredentials {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotarizationCredentials.class);

    @Inject
    @ConfigProperty(name = "notarization.appleid.password")
    private Optional<String> appleIDPassword;

    @Inject
    @ConfigProperty(name = "notarization.appleid.username")
    private Optional<String> appleIDUsername;

    @Inject
    @ConfigProperty(name = "notarization.appleid.teamid")
    private Optional<String> appleIDTeamID;

    @Inject
    @ConfigProperty(name = "notarization.appleapi.keyfile")
    private Optional<String> appleApiKeyFile;

    public boolean requireUsername() {
        if (appleIDUsername.isEmpty()) {
            LOGGER.error("required configuration value 'notarization.appleid.username' is missing");
            return false;
        } else {
            return true;
        }
    }

    public boolean requirePassword() {
        if (appleIDUsername.isEmpty()) {
            LOGGER.error("required configuration value 'notarization.appleid.password' is missing");
            return false;
        } else {
            return true;
        }
    }

    public boolean requireTeamID() {
        if (appleIDUsername.isEmpty()) {
            LOGGER.error("required configuration value 'notarization.appleid.teamid' is missing");
            return false;
        } else {
            return true;
        }
    }

    public boolean requireAppleApiKeyFile() {
        if (appleApiKeyFile.isEmpty()) {
            LOGGER.error("required configuration value 'notarization.appleapi.keyfile' is missing");
            return false;
        } else {
            return true;
        }
    }

    public String getUsername() {
        return appleIDUsername.get();
    }

    public boolean hasPassword() {
        return appleIDPassword.isPresent();
    }

    public String getPassword() {
        return appleIDPassword.get();
    }

    public String getTeamID() {
        return appleIDTeamID.get();
    }

    public String getAppleApiKeyFile() {
        return appleApiKeyFile.get();
    }
}
