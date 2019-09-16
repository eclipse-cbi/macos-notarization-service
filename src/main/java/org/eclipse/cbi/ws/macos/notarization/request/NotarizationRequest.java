package org.eclipse.cbi.ws.macos.notarization.request;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.cbi.ws.macos.notarization.request.NotarizationStatus.State;
import org.eclipse.cbi.ws.macos.notarization.xcrun.altool.NotarizationInfoResult;
import org.eclipse.cbi.ws.macos.notarization.xcrun.altool.NotarizerResult;
import org.eclipse.cbi.ws.macos.notarization.xcrun.altool.StaplerResult;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class NotarizationRequest {
	
	public abstract Path fileToNotarize();
	
	@Nullable
	public abstract String submittedFilename();

	public abstract NotarizationRequestOptions notarizationOptions();

	abstract Supplier<NotarizerResult> notarizer();

	abstract Function<? super NotarizerResult, ? extends NotarizationInfoResult> notarizationInfo();

	abstract Optional<Function<? super NotarizationInfoResult, ? extends StaplerResult>> staplerResult();

	public abstract Future<NotarizationStatus> request();
	
	public abstract AtomicReference<NotarizationStatus> status();

	public static Builder builder() {
		return new AutoValue_NotarizationRequest.Builder()
				.status(new AtomicReference<NotarizationStatus>(NotarizationStatus.builder().status(State.IN_PROGRESS).message("Uploading file to Apple notarization service").build()));
	}

	@AutoValue.Builder
	public abstract static class Builder {
		public abstract Builder fileToNotarize(Path fileToNotarize);
		
		public abstract Builder submittedFilename(String submittedFilename);

		public abstract Builder notarizationOptions(NotarizationRequestOptions notarizationOptions);
		
		public abstract Builder request(Future<NotarizationStatus> request);

		public abstract Builder notarizer(Supplier<NotarizerResult> notarizer);
		
		abstract Supplier<NotarizerResult> notarizer();

		public abstract Builder notarizationInfo(Function<? super NotarizerResult, ? extends NotarizationInfoResult> notarizationInfo);
		
		abstract Function<? super NotarizerResult, ? extends NotarizationInfoResult> notarizationInfo();
		
		public abstract Builder staplerResult(Function<? super NotarizationInfoResult, ? extends StaplerResult> staplerResult);
		
		abstract Optional<Function<? super NotarizationInfoResult, ? extends StaplerResult>> staplerResult();
		
		public abstract Builder status(AtomicReference<NotarizationStatus> status);
		
		abstract AtomicReference<NotarizationStatus> status();

		abstract NotarizationRequest autoBuild();
		
		public NotarizationRequest build(Executor executor) {	
			CompletableFuture<? extends NotarizationInfoResult> request = CompletableFuture.supplyAsync(notarizer(), executor)
				.whenComplete(this::updateStatus)
				.thenApply(notarizationInfo())
				.whenComplete(this::updateStatus);
			
			if (staplerResult().isPresent()) {
				request(
					request.thenApply(staplerResult().get())
					.whenComplete(this::updateStatus)
					.thenApply(r -> status().get()));
			} else {
				request(
					request.thenApply(r -> {
						if (status().get().status() == State.IN_PROGRESS) {
							status().set(status().get().toBuilder().status(State.COMPLETE).build());
						} 
						return status().get();
					}));
			}
			
			return autoBuild();
		}

		private void updateStatus(NotarizerResult result, Throwable throwable) {
			NotarizationStatus.Builder statusBuilder = NotarizationStatus.builder();
			if (throwable != null) {
				statusBuilder.status(State.ERROR).message("Error happened while uploading file to Apple notarization service").moreInfo(throwable.getMessage());
			} else if (status().get().status() == State.ERROR) {
				throw new RuntimeException(status().get().toString());
			} else {
				switch (result.status()) {
					case UPLOAD_FAILED:
						statusBuilder.status(State.ERROR)
						.message("Issue happened while uploading file to Apple notarization service");
						break;
					case UPLOAD_SUCCESSFUL:
						statusBuilder.status(State.IN_PROGRESS)
						.message("File has been successfully uploaded to Apple notarization service");
						break;
					default:
						throw new IllegalStateException("Unknown status " + result.status());
				}
				statusBuilder.moreInfo(result.message());
			}
			Builder.this.status().set(statusBuilder.build());
		}
		
		private void updateStatus(NotarizationInfoResult result, Throwable throwable) {
			NotarizationStatus.Builder statusBuilder = NotarizationStatus.builder();
			if (throwable != null) {
				statusBuilder.status(State.ERROR).message("Error happened while uploading file to Apple notarization service").moreInfo(throwable.getMessage());
			} else if (status().get().status() == State.ERROR) {
				throw new RuntimeException(status().get().toString());
			} else {
				switch (result.status()) {
					case NOTARIZATION_FAILED:
						statusBuilder.status(State.ERROR)
						.message("Notarization has failed on Apple notarization service");
						break;
					case RETRIEVAL_FAILED:
						statusBuilder.status(State.ERROR)
						.message("Apple notarization service fails to report progress");
						break;
					case NOTARIZATION_IN_PROGRESS:
						statusBuilder.status(State.ERROR)
						.message("Apple notarization service reports notarization in progress for too long");
						break;
					case NOTARIZATION_SUCCESSFUL:
						statusBuilder.status(State.IN_PROGRESS)
						.message("Notarization has successfully completed on Apple notarization service");
						break;
					default:
						throw new IllegalStateException("Unknown status " + result.status());
				}
				statusBuilder.moreInfo(result.message());
			}
			Builder.this.status().set(statusBuilder.build());
		}
		
		private void updateStatus(StaplerResult result, Throwable throwable) {
			NotarizationStatus.Builder statusBuilder = NotarizationStatus.builder();
			if (throwable != null) {
				statusBuilder.status(State.ERROR).message("Error happened while stapling notarization ticket to uploaded file").moreInfo(throwable.getMessage());
			} else if (status().get().status() == State.ERROR) {
				throw new RuntimeException(status().get().toString());
			} else {
				switch (result.status()) {
					case SUCCESS:
						statusBuilder.status(State.COMPLETE)
						.message("Notarization ticket has been stapled successfully to uploaded file. You can now download the stapled file");
						break;
					case ERROR:
						statusBuilder.status(State.ERROR)
						.message("Error happened while stapling notarization ticket to uploaded file. Notarization has been successful though");
						break;
					default:
						throw new IllegalStateException("Unknown status " + result.status());
				}
				statusBuilder.moreInfo(result.message());
			}
			Builder.this.status().set(statusBuilder.build());
		}
	}
}