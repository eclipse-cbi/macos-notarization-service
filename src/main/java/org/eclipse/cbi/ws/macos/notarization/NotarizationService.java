package org.eclipse.cbi.ws.macos.notarization;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.eclipse.cbi.ws.macos.notarization.request.NotarizationRequest;
import org.eclipse.cbi.ws.macos.notarization.request.NotarizationRequestOptions;
import org.eclipse.cbi.ws.macos.notarization.request.NotarizationStatus;
import org.eclipse.cbi.ws.macos.notarization.request.NotarizationStatusWithUUID;
import org.eclipse.cbi.ws.macos.notarization.xcrun.altool.NotarizationInfo;
import org.eclipse.cbi.ws.macos.notarization.xcrun.altool.NotarizationInfoResult;
import org.eclipse.cbi.ws.macos.notarization.xcrun.altool.Notarizer;
import org.eclipse.cbi.ws.macos.notarization.xcrun.altool.NotarizerResult;
import org.eclipse.cbi.ws.macos.notarization.xcrun.altool.Stapler;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.moshi.Moshi;

import okhttp3.OkHttpClient;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@javax.ws.rs.Path("/macos-notarization-service")
public class NotarizationService {

	private static final Logger LOGGER = LoggerFactory.getLogger(NotarizationService.class);

	@Inject
	NotarizationCache cache;

	@Inject
	Moshi moshi;

	@Inject
	OkHttpClient httpClient;

	@Inject
	ScheduledExecutorService executor;

	@Inject
	@ConfigProperty(name = "notarization.cache.uploadedFiles", defaultValue = "/tmp/macos-notarization-service/pending-files")
	String pendingFilesPath;

	@Inject
	@ConfigProperty(name = "notarization.appleid.password")
	String appleIDPassword;

	@Inject
	@ConfigProperty(name = "notarization.appleid.username")
	String appleIDUsername;

	@Inject
	@ConfigProperty(name = "notarization.upload.timeout", defaultValue = "PT20M")
	Duration uploadTimeout;

	@Inject
	@ConfigProperty(name = "notarization.upload.maxAttempts", defaultValue = "3")
	int uploadMaxAttempts;

	@Inject
	@ConfigProperty(name = "notarization.upload.minBackOffDelay", defaultValue = "PT10S")
	Duration uploadMinBackOffDelay;

	@Inject
	@ConfigProperty(name = "notarization.upload.maxBackOffDelay", defaultValue = "PT60S")
	Duration uploadMaxBackOffDelay;

	@Inject
	@ConfigProperty(name = "notarization.infoPolling.maxTotalDuration", defaultValue = "PT6H")
	Duration infoPollingMaxTotalDuration;

	@Inject
	@ConfigProperty(name = "notarization.infoPolling.delayBetweenSuccessfulAttempts", defaultValue = "PT20S")
	Duration infoPollingDelayBetweenSuccessfulAttempts;

	@Inject
	@ConfigProperty(name = "notarization.infoPolling.maxFailedAttempts", defaultValue = "8")
	int infoPollingMaxFailedAttempts;

	@Inject
	@ConfigProperty(name = "notarization.infoPolling.minBackOffDelay", defaultValue = "PT2S")
	Duration infoPollingMinBackOffDelay;

	@Inject
	@ConfigProperty(name = "notarization.infoPolling.maxBackOffDelay", defaultValue = "PT60S")
	Duration infoPollingMaxBackOffDelay;

	@Inject
	@ConfigProperty(name = "notarization.infoPolling.timeout", defaultValue = "PT30S")
	Duration infoPollingTimeout;

	@Inject
	@ConfigProperty(name = "notarization.stapling.timeout", defaultValue = "PT2M")
	Duration staplingTimeout;

	@Inject
	@ConfigProperty(name = "notarization.stapling.maxAttempts", defaultValue = "3")
	int staplingMaxAttempts;

	@Inject
	@ConfigProperty(name = "notarization.stapling.minBackOffDelay", defaultValue = "PT10S")
	Duration staplingMinBackOffDelay;

	@Inject
	@ConfigProperty(name = "notarization.stapling.maxBackOffDelay", defaultValue = "PT60S")
	Duration staplingMaxBackOffDelay;

	@POST
	@javax.ws.rs.Path("notarize")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	public Response notarize(MultipartFormDataInput input) throws IOException, ServletException {
		try (MultipartFormDataInputWrapper.WithMoshi formData = new MultipartFormDataInputWrapper.WithMoshi(input, moshi)) {
			Optional<NotarizationRequestOptions> optionsFromEquest = formData.partJsonBodyAs("options", NotarizationRequestOptions.class);
			Optional<InputStream> fileFromRequest = formData.partBodyAs("file", InputStream.class);

			if (optionsFromEquest.isPresent() && fileFromRequest.isPresent()) {
				InputStream file = fileFromRequest.get();
				NotarizationRequestOptions options = optionsFromEquest.get();
				LOGGER.trace("notarization options:" + options);

				NotarizationStatusWithUUID response = notarize(formData, file, options);
				LOGGER.trace("notarization response:" + response);
				return toJsonResponse(response);
			} else {
				return Response.status(Response.Status.BAD_REQUEST)
						.entity("Request must be a multipart/form-data with a 'file' (application/octet-stream) and an 'options' (application/json) parts.")
						.type(MediaType.TEXT_PLAIN).build();
			}
		}
	}

	private NotarizationStatusWithUUID notarize(MultipartFormDataInputWrapper.WithMoshi formData, InputStream file, NotarizationRequestOptions options)
			throws IOException {
		Path fileToNotarize = createTempFile(Paths.get(pendingFilesPath), formData.submittedFilename("file").orElse("unknown"));

		Files.copy(file, fileToNotarize);

		NotarizationRequest.Builder requestBuilder = NotarizationRequest.builder().fileToNotarize(fileToNotarize)
				.submittedFilename(formData.submittedFilename("file").orElse(null)).notarizationOptions(options);

		requestBuilder.notarizer(() -> Notarizer.builder()
				.primaryBundleId(options.primaryBundleId())
				.appleIDUsername(appleIDUsername)
				.appleIDPassword(appleIDPassword)
				.fileToNotarize(fileToNotarize)
				.uploadTimeout(uploadTimeout)
				.build()
				.uploadFailsafe(uploadMaxAttempts, uploadMinBackOffDelay, uploadMaxBackOffDelay));

		requestBuilder.notarizationInfo((NotarizerResult r) -> NotarizationInfo.builder()
				.appleIDUsername(appleIDUsername)
				.appleIDPassword(appleIDPassword)
				.appleRequestUUID(r.appleRequestUUID())
				.httpClient(httpClient)
				.pollingTimeout(infoPollingTimeout)
				.build()
				.retrieveInfoFailsafe(infoPollingMaxTotalDuration, infoPollingDelayBetweenSuccessfulAttempts, infoPollingMaxFailedAttempts, infoPollingMinBackOffDelay,
						infoPollingMaxBackOffDelay));

		if (options.staple()) {
			requestBuilder.staplerResult((NotarizationInfoResult r) -> Stapler.builder()
					.fileToStaple(fileToNotarize)
					.staplingTimeout(staplingTimeout)
					.build()
					.stapleFailsafe(staplingMaxAttempts, staplingMinBackOffDelay, staplingMaxBackOffDelay));
		}

		NotarizationRequest request = requestBuilder.build(executor);

		UUID uuid = cache.put(request);

		NotarizationStatusWithUUID response = NotarizationStatusWithUUID.from(uuid, request.status().get());
		return response;
	}

	@GET
	@javax.ws.rs.Path("{uuid}/status")
	@Produces(MediaType.APPLICATION_JSON)
	public Response status(@PathParam(value = "uuid") String uuid) throws InterruptedException, ExecutionException {
		try {
			UUID fromString = UUID.fromString(uuid);
			NotarizationRequest request = cache.getIfPresent(fromString);
			if (request == null) {
				return Response.status(Response.Status.NOT_FOUND).entity("Unkown UUID").type(MediaType.TEXT_PLAIN).build();
			} else {
				return toJsonResponse(NotarizationStatusWithUUID.from(fromString, request.status().get()));
			}
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).type(MediaType.TEXT_PLAIN).build();
		}
	}

	@GET
	@javax.ws.rs.Path("{uuid}/download")
	@Produces(MediaType.APPLICATION_JSON)
	public Response download(@PathParam(value = "uuid") String uuid) throws InterruptedException, ExecutionException, IOException {
		UUID fromString = UUID.fromString(uuid);
		NotarizationRequest request = cache.getIfPresent(fromString);
		if (request == null) {
			return Response.status(Response.Status.NOT_FOUND).entity("Unkown UUID").type(MediaType.TEXT_PLAIN).build();
		} else {
			if (request.status().get().status() == NotarizationStatus.State.COMPLETE) {
				ResponseBuilder response = Response.ok(Files.newInputStream(request.fileToNotarize()), MediaType.APPLICATION_OCTET_STREAM);
				if (request.submittedFilename() != null) {
					return response.header("Content-Disposition", "attachment; filename=\"" + request.submittedFilename() + "\"").build();
				}
				return response.header("Content-Disposition", "attachment").build();
			} else {
				return Response.status(Response.Status.NOT_FOUND).entity("Notarization process did not complete yet.").type(MediaType.TEXT_PLAIN).build();
			}
		}
	}

	private Response toJsonResponse(NotarizationStatusWithUUID response) {
		return Response.ok(moshi.adapter(NotarizationStatusWithUUID.class).toJson(response), MediaType.APPLICATION_JSON).build();
	}

	static Path createTempFile(Path parentFolder, String templateFilename) throws IOException {
		return Files.createTempFile(parentFolder, com.google.common.io.Files.getNameWithoutExtension(templateFilename) + "-",
				"." + com.google.common.io.Files.getFileExtension(templateFilename));

	}

}