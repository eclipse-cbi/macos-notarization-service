/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.OkHttpClient;

@ApplicationScoped
public class Producer {
	private static final Logger LOGGER = LoggerFactory.getLogger("macos-notarization-service-threadpool-handler");

	@Produces
	@Named("macos-notarization-service-pool")
	ScheduledExecutorService produceExecutor() {
		return Executors.newScheduledThreadPool(32, new ThreadFactoryBuilder()
				.setNameFormat("macos-notarization-service-pool-thread-%d")
				.setUncaughtExceptionHandler((t, e) -> LOGGER.error("Uncaught " + e.getClass().getName() + " in thread " + t.getName(), e))
				.build());
	}

	@Produces
	Moshi produceMoshi() {
		return new Moshi.Builder()
				.add(UUID.class,
					new JsonAdapter<UUID>() {
						public UUID fromJson(JsonReader reader) throws IOException {
							return UUID.fromString(reader.nextString());
						}

						public void toJson(JsonWriter writer, UUID value) throws IOException {
							writer.value(value.toString());
						}
					})
				.build();
	}

	@Produces
	OkHttpClient produceOkHttpClient() {
		return new OkHttpClient.Builder().callTimeout(Duration.ofSeconds(30)).build();
	}
}
