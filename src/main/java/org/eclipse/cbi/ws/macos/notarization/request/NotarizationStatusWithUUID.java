package org.eclipse.cbi.ws.macos.notarization.request;

import java.util.UUID;

import com.google.auto.value.AutoValue;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

@AutoValue
public abstract class NotarizationStatusWithUUID {

	public abstract UUID uuid();
	
	public abstract NotarizationStatus notarizationStatus();
	
	public static NotarizationStatusWithUUID from(UUID uuid, NotarizationStatus status) {
		return new AutoValue_NotarizationStatusWithUUID(uuid, status);
	}
	
	static JsonAdapter<NotarizationStatusWithUUID> jsonAdapter(Moshi moshi) {
		return new AutoValue_NotarizationStatusWithUUID.MoshiJsonAdapter(moshi);
	}
	
}
