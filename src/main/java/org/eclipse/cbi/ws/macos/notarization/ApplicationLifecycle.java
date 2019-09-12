package org.eclipse.cbi.ws.macos.notarization;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class ApplicationLifecycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationLifecycle.class);

  @Inject
  @ConfigProperty(name = "notarization.cache.uploadedFiles", defaultValue = "/tmp/macos-notarization-service/pending-files")
  String pendingFiles;

  void onStart(@Observes StartupEvent ev) throws IOException {
    Path pendingFilesPath = Paths.get(pendingFiles);

    if (!Files.isDirectory(pendingFilesPath)) {
      LOGGER.info("Creating folder '{}'", pendingFilesPath);
      Files.createDirectories(pendingFilesPath);
    } else {
      LOGGER.info("Cleaning up folder '{}'", pendingFilesPath);
      Files.walk(pendingFilesPath)
      .map(Path::toFile)
      .forEach(File::delete);
      Files.createDirectories(pendingFilesPath);
    }
  }

  void onStop(@Observes ShutdownEvent ev) {
    LOGGER.info("The application is stopping...");
  }

}
