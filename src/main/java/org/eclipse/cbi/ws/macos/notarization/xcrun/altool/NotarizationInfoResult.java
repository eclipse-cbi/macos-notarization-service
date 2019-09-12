package org.eclipse.cbi.ws.macos.notarization.xcrun.altool;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class NotarizationInfoResult {
	public enum Status {NOTARIZATION_IN_PROGRESS, NOTARIZATION_FAILED, NOTARIZATION_SUCCESSFUL, RETRIEVAL_FAILED}
	public abstract Status status();
	public abstract String message();
	public abstract String notarizationLog();
	static NotarizationInfoResult.Builder builder() {
		return new AutoValue_NotarizationInfoResult.Builder();
	}
	@AutoValue.Builder
	static abstract class Builder {
		abstract NotarizationInfoResult.Builder status(Status status);
		abstract NotarizationInfoResult.Builder message(String message);
		abstract NotarizationInfoResult.Builder notarizationLog(String notarizationLog);
		abstract NotarizationInfoResult build();
	}
}