package org.eclipse.cbi.ws.macos.notarization;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

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
  @ConfigProperty(name="notarization.cache.expireAfterWrite", defaultValue = "P1D") String cacheExpireAfterWrite) {
    cache = CacheBuilder.newBuilder()
      .expireAfterWrite(Duration.parse(cacheExpireAfterWrite))
      .recordStats()
      .removalListener(RemovalListeners.asynchronous((RemovalNotification<UUID, NotarizationRequest> notification) -> {
	    LOGGER.trace("Removing expired request {} from cache", notification.getValue());
	    if (!notification.getValue().request().isDone()) {
		  LOGGER.warn("The notarization background process was not done before removal from cache. It will be cancelled");
		  notification.getValue().request().cancel(true);
	    }
        try {
          Files.deleteIfExists(notification.getValue().fileToNotarize());
        } catch (IOException e) {
          LOGGER.warn(String.format("Unable to delete user uploaded file to notarize after cache eviction of\n%s", notification.getValue()), e);
        }
      }, Executors.newSingleThreadExecutor()))
      .build();
  }

  UUID put(NotarizationRequest request) {
    UUID uuid;
    try {
      // avoid (statistically impossible) potential collision
      do  {
        uuid = UUID.randomUUID();
      } while (cache.get(uuid, () -> request) != request);
    } catch(ExecutionException e) {
      throw new RuntimeException(e);
    }
    LOGGER.trace("Added request {} to cache (uuid={})", request, uuid);
    return uuid;
  }

  NotarizationRequest getIfPresent(UUID uuid) {
    return cache.getIfPresent(uuid);
  }
}