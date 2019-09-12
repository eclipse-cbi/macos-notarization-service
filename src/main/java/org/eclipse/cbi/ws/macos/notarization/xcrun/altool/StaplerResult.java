package org.eclipse.cbi.ws.macos.notarization.xcrun.altool;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class StaplerResult {
	public enum Status {ERROR, SUCCESS}
	public abstract StaplerResult.Status status();
	public abstract String message();
}