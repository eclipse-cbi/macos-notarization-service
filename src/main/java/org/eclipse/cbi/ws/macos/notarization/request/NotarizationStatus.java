package org.eclipse.cbi.ws.macos.notarization.request;

import javax.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

@AutoValue
public abstract class NotarizationStatus {
	public enum State { COMPLETE, IN_PROGRESS, ERROR };
	public abstract NotarizationStatus.State status();

	public abstract String message();

	@Nullable
	public abstract String moreInfo();

	@Nullable
	public abstract String log();

	abstract NotarizationStatus.Builder toBuilder();

	static NotarizationStatus.Builder builder() {
		return new AutoValue_NotarizationStatus.Builder();
	}

	@AutoValue.Builder
	abstract static class Builder {
		abstract NotarizationStatus.Builder status(NotarizationStatus.State status);

		abstract NotarizationStatus.Builder message(String message);

		abstract NotarizationStatus.Builder moreInfo(String moreInfo);

		abstract NotarizationStatus.Builder log(String log);

		abstract NotarizationStatus build();
	}

	static JsonAdapter<NotarizationStatus> jsonAdapter(Moshi moshi) {
		return new AutoValue_NotarizationStatus.MoshiJsonAdapter(moshi);
	}
}