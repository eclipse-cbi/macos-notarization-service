/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import jakarta.ws.rs.core.GenericType;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import okio.BufferedSource;
import okio.Okio;

class MultipartFormDataInputWrapper implements AutoCloseable {

	private static final Splitter SPLITTER_ON_EQUALSIGN = Splitter.on('=').trimResults().omitEmptyStrings();
	private static final Splitter SPLITTER_ON_SEMICOLON = Splitter.on(';').trimResults().omitEmptyStrings();
	private final MultipartFormDataInput input;

	MultipartFormDataInputWrapper(MultipartFormDataInput input) {
		this.input = input;
	}

	Optional<InputPart> partWithName(String name) {
		List<InputPart> parts = input.getFormDataMap().get(name);
		if (parts != null && !parts.isEmpty()) {
			return Optional.ofNullable(parts.iterator().next());
		}
		return Optional.empty();
	}

	Optional<String> submittedFilename(String partName) {
		Optional<InputPart> part = partWithName(partName);
		if (part.isPresent()) {
			String cdHeader = part.get().getHeaders().getFirst("Content-Disposition");

			if (!Strings.isNullOrEmpty(cdHeader)) {
				List<String> filenames = StreamSupport.stream(SPLITTER_ON_SEMICOLON.split(cdHeader).spliterator(), false)
						.filter(n -> n.startsWith("filename"))
						.map(SPLITTER_ON_EQUALSIGN::splitToList)
						.filter(l -> l.size() > 1)
						.findFirst()
						.orElse(Collections.emptyList());
				if (!filenames.isEmpty()) {
					return Optional.of(filenames.get(1).replaceAll("\"", ""));
				}
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

	Optional<String> partBodyAsString(String partName) throws IOException {
		Optional<InputPart> part = partWithName(partName);
		if (part.isPresent()) {
			return Optional.of(part.get().getBodyAsString());
		}
		return Optional.empty();
	}

	Optional<BufferedSource> partBodyAsBufferedSource(String partName) throws IOException {
		Optional<InputPart> part = partWithName(partName);
		if (part.isPresent()) {
			return Optional.of(Okio.buffer(Okio.source(part.get().getBody(InputStream.class, null))));
		}
		return Optional.empty();
	}

	<T> Optional<T> partBodyAs(String partName, Class<T> type) throws IOException {
		return partBodyAs(partName, type, null);
	}

	<T> Optional<T> partBodyAs(String partName, Class<T> type, Type genericType) throws IOException {
		Optional<InputPart> part = partWithName(partName);
		if (part.isPresent()) {
			return Optional.of(part.get().getBody(type, genericType));
		}
		return Optional.empty();
	}

	<T> Optional<T> partBodyAs(String partName, GenericType<T> genericType) throws IOException {
		Optional<InputPart> part = partWithName(partName);
		if (part.isPresent()) {
			return Optional.of(part.get().getBody(genericType));
		}
		return Optional.empty();
	}

	@Override
	public void close() {
		input.close();
	}
}