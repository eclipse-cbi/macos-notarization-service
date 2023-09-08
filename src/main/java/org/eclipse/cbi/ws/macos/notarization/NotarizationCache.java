/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListeners;
import com.google.common.cache.RemovalNotification;

import org.eclipse.cbi.ws.macos.notarization.request.NotarizationRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class NotarizationCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotarizationCache.class);

    private final Cache<UUID, NotarizationRequest> cache;

    @Inject
    NotarizationCache(
            @ConfigProperty(name = "notarization.cache.expireAfterWrite", defaultValue = "P1D") String cacheExpireAfterWrite) {
        cache = CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.parse(cacheExpireAfterWrite))
                .recordStats()
                .removalListener(RemovalListeners.asynchronous((RemovalNotification<UUID, NotarizationRequest> notification) -> {
                    final NotarizationRequest request = notification.getValue();
                    LOGGER.trace("Removing expired request {} from cache", request);
                    if (!request.request().isDone()) {
                        LOGGER.warn("The notarization background process was not done before removal from cache. It will be cancelled");
                        request.request().cancel(true);
                    }
                    try {
                        Files.deleteIfExists(request.fileToNotarize());
                    } catch (IOException e) {
                        LOGGER.warn(String.format("Unable to delete user uploaded file '%s' to notarize after cache eviction of\n%s", request.fileToNotarize(), request), e);
                    }
                }, Executors.newSingleThreadExecutor()))
                .build();
    }

    UUID put(NotarizationRequest request) {
        UUID uuid;
        try {
            // avoid (statistically impossible) potential collision
            do {
                uuid = UUID.randomUUID();
            } while (cache.get(uuid, () -> request) != request);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        LOGGER.trace("Added request {} to cache (uuid={})", request, uuid);
        return uuid;
    }

    NotarizationRequest getIfPresent(UUID uuid) {
        return cache.getIfPresent(uuid);
    }
}