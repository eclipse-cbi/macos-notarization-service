package org.eclipse.cbi.ws.macos.notarization.request;

import com.google.auto.value.AutoValue;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

@AutoValue
public abstract class NotarizationRequestOptions {
	public abstract String primaryBundleId();

	public abstract boolean staple();

	static NotarizationRequestOptions.Builder builder() {
		return new AutoValue_NotarizationRequestOptions.Builder().staple(false);
	}

	@AutoValue.Builder
	abstract static class Builder {
		abstract NotarizationRequestOptions.Builder primaryBundleId(String primaryBundleId);

		abstract NotarizationRequestOptions.Builder staple(boolean staple);

		abstract NotarizationRequestOptions build();
	}

	static JsonAdapter<NotarizationRequestOptions> jsonAdapter(Moshi moshi) {
		return new AutoValue_NotarizationRequestOptions.MoshiJsonAdapter(moshi);
	}
}