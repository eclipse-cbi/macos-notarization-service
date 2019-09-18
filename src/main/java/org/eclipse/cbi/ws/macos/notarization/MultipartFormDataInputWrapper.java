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
import com.squareup.moshi.Moshi;

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

	static class WithMoshi extends MultipartFormDataInputWrapper {
		private final Moshi moshi;

		WithMoshi(MultipartFormDataInput input, Moshi moshi) {
			super(input);
			this.moshi = moshi;
		}

		<T> Optional<T> partJsonBodyAs(String partName, Class<T> type) throws IOException {
			if (moshi != null) {
				Optional<BufferedSource> source = partBodyAsBufferedSource(partName);
				if (source.isPresent()) {
					try (BufferedSource actualSource = source.get()) {
						return Optional.of(moshi.adapter(type).fromJson(actualSource));
					}
				}
			}
			return Optional.empty();
		}
	}

	@Override
	public void close() {
		input.close();
	}
}