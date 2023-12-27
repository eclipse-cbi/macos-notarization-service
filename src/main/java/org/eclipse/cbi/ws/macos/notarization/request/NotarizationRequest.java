/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
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

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.cbi.ws.macos.notarization.request.NotarizationStatus.State;
import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizationInfoResult;
import org.eclipse.cbi.ws.macos.notarization.execution.result.NotarizerResult;
import org.eclipse.cbi.ws.macos.notarization.execution.result.StaplerResult;

@RecordBuilder
public record NotarizationRequest(
		Path fileToNotarize,
		@Nullable String submittedFilename,
		NotarizationRequestOptions notarizationOptions,
		Supplier<NotarizerResult> notarizer,
		Function<? super NotarizerResult, ? extends NotarizationInfoResult> notarizationInfo,
		Optional<Function<? super NotarizationInfoResult, ? extends StaplerResult>> staplerResult,
		Future<NotarizationStatus> request,
		AtomicReference<NotarizationStatus> status) {

	public static NotarizationRequestBuilder builder() {
		return NotarizationRequestBuilder.builder();
	}

	public static NotarizationRequestBuilder builderWithDefaultStatus() {
		return
			builder()
				.status(new AtomicReference<>(
							NotarizationStatus.builder()
								.status(State.IN_PROGRESS)
								.message("Uploading file to Apple notarization service")
								.build()));
	}

	public NotarizationRequest execute(Executor executor) {
		CompletableFuture<? extends NotarizationInfoResult> future =
			CompletableFuture.supplyAsync(notarizer, executor)
				.whenComplete(this::updateNotarizerStatus)
				.thenApply(notarizationInfo)
				.whenComplete(this::updateNotarizationInfoStatus);

		CompletableFuture<NotarizationStatus> result;
		if (staplerResult.isPresent()) {
			result =
				future.thenApply(staplerResult.get())
					  .whenComplete(this::updateStaplerStatus)
					  .thenApply(r -> status.get());
		} else {
			result =
				future.thenApply(r -> {
					if (status.get().status() == State.IN_PROGRESS) {
						status.set(NotarizationStatusBuilder.builder(status.get()).status(State.COMPLETE).build());
					}
					return status.get();
				});
		}

		return NotarizationRequestBuilder.from(this).withRequest(result);
	}

	private void updateNotarizerStatus(NotarizerResult result, Throwable throwable) {
		if (status.get().status() != State.ERROR) {
			NotarizationStatusBuilder statusBuilder = NotarizationStatusBuilder.builder();
			if (throwable != null) {
				statusBuilder
					.status(State.ERROR)
					.message("Error happened while uploading file to Apple notarization service")
					.moreInfo(throwable.getMessage());
			} else {
				switch (result.status()) {
					case UPLOAD_FAILED:
						statusBuilder
							.status(State.ERROR)
							.message("Issue happened while uploading file to Apple notarization service");
						break;
					case UPLOAD_SUCCESSFUL:
						statusBuilder
							.status(State.IN_PROGRESS)
							.message("File has been successfully uploaded to Apple notarization service");
						break;
					default:
						throw new IllegalStateException("Unknown status " + result.status());
				}
				statusBuilder.moreInfo(result.message());
			}
			status.set(statusBuilder.build());
		}
	}

	private void updateNotarizationInfoStatus(NotarizationInfoResult result, Throwable throwable) {
		if (status.get().status() != State.ERROR) {
			NotarizationStatusBuilder statusBuilder = NotarizationStatusBuilder.builder();
			if (throwable != null) {
				statusBuilder
					.status(State.ERROR)
					.message("Error happened while uploading file to Apple notarization service")
					.moreInfo(throwable.getMessage());
			} else {
				switch (result.status()) {
					case NOTARIZATION_FAILED:
						statusBuilder
							.status(State.ERROR)
							.message("Notarization has failed on Apple notarization service");
						break;
					case RETRIEVAL_FAILED:
						statusBuilder
							.status(State.ERROR)
							.message("Apple notarization service fails to report progress");
						break;
					case NOTARIZATION_IN_PROGRESS:
						statusBuilder
							.status(State.ERROR)
							.message("Apple notarization service reports notarization in progress for too long");
						break;
					case NOTARIZATION_SUCCESSFUL:
						statusBuilder
							.status(State.IN_PROGRESS)
							.message("Notarization has successfully completed on Apple notarization service");
						break;
					default:
						throw new IllegalStateException("Unknown status " + result.status());
				}
				statusBuilder.log(result.notarizationLog());
				statusBuilder.moreInfo(result.message());
			}
			status.set(statusBuilder.build());
		}
	}

	private void updateStaplerStatus(StaplerResult result, Throwable throwable) {
		if (status.get().status() != State.ERROR) {
			NotarizationStatusBuilder statusBuilder = NotarizationStatusBuilder.builder();
			if (throwable != null) {
				statusBuilder
					.status(State.ERROR)
					.message("Error happened while stapling notarization ticket to uploaded file.")
					.moreInfo(throwable.getMessage());
			} else {
				switch (result.status()) {
					case SUCCESS:
						statusBuilder
							.status(State.COMPLETE)
							.message("Notarization ticket has been stapled successfully to uploaded file. You can now download the stapled file.")
							.log(status.get().log());
						break;
					case ERROR:
						statusBuilder
							.status(State.ERROR)
							.message("Error happened while stapling notarization ticket to uploaded file. Notarization has been successful though.")
							.log(status.get().log());
						break;
					default:
						throw new IllegalStateException("Unknown status " + result.status());
				}
				statusBuilder.moreInfo(result.message());
			}
			status.set(statusBuilder.build());
		}
	}
}