/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.eclipse.cbi.ws.macos.notarization.xcrun.common.NotarizationTool;
import org.eclipse.cbi.ws.macos.notarization.xcrun.notarytool.NotarytoolNotarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

	/**
	 * Returns the actual notarization tool that will be used.
	 * This is useful for mocking the used tool during tests.
	 */
	@Produces
	@ApplicationScoped
	public NotarizationTool produceNotarizationTool() {
		// when altool shall be used
		// OkHttpClient httpClient = new OkHttpClient.Builder().callTimeout(Duration.ofSeconds(30)).build();
		//return new AltoolNotarizer(httpClient);

		return new NotarytoolNotarizer();
	}
}
