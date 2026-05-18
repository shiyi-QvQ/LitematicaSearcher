package com.litematicasearcher.client.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class RedenApiClient {
	public static final int DEFAULT_LIMIT = 24;
	public static final int DEFAULT_OFFSET = 0;

	private static final String USER_AGENT = "LitematicaSearcher/1.0.0 Minecraft-Fabric";
	private static final String BASE_URL = "https://redenmc.com/api/mc-services";
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

	private final HttpClient httpClient;

	public RedenApiClient() {
		this(HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build());
	}

	RedenApiClient(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	public CompletableFuture<RedenSearchResponse> search(String query) {
		return search(query, DEFAULT_LIMIT, DEFAULT_OFFSET);
	}

	public CompletableFuture<RedenSearchResponse> search(String query, int limit, int offset) {
		if (limit <= 0) {
			throw new IllegalArgumentException("limit must be greater than 0");
		}

		if (offset < 0) {
			throw new IllegalArgumentException("offset must not be negative");
		}

		String normalizedQuery = query == null ? "" : query;
		URI uri = URI.create(BASE_URL + "/litematica/search?q=" + encodeQuery(normalizedQuery)
				+ "&limit=" + limit
				+ "&offset=" + offset);

		return sendJsonRequest(uri).thenApply(RedenSearchResponse::fromJson);
	}

	public CompletableFuture<Optional<RedenMachine>> fetchDetails(String machineKey, RedenApiLanguage language) {
		if (machineKey == null || machineKey.isBlank()) {
			throw new IllegalArgumentException("machineKey must not be blank");
		}

		RedenApiLanguage resolvedLanguage = language == null ? RedenApiLanguage.EN : language;
		URI uri = URI.create(BASE_URL + "/yisibite/"
				+ encodePathSegment(machineKey)
				+ "/info/"
				+ resolvedLanguage.apiCode());

		return sendJsonRequest(uri).thenApply(this::readFirstMachine);
	}

	private CompletableFuture<JsonObject> sendJsonRequest(URI uri) {
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", "application/json")
				.header("User-Agent", USER_AGENT)
				.GET()
				.build();

		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.thenApply(response -> readJsonResponse(uri, response));
	}

	private JsonObject readJsonResponse(URI uri, HttpResponse<String> response) {
		int status = response.statusCode();

		if (status < 200 || status >= 300) {
			throw new RedenApiException("RedenMC API returned HTTP " + status + " for " + uri);
		}

		try {
			JsonElement element = JsonParser.parseString(response.body());

			if (!element.isJsonObject()) {
				throw new RedenApiException("RedenMC API returned a non-object JSON response for " + uri);
			}

			return element.getAsJsonObject();
		} catch (RuntimeException exception) {
			if (exception instanceof RedenApiException) {
				throw exception;
			}

			throw new RedenApiException("Failed to parse RedenMC API response for " + uri, exception);
		}
	}

	private Optional<RedenMachine> readFirstMachine(JsonObject object) {
		for (JsonElement element : RedenJson.array(object, "d")) {
			if (element.isJsonObject()) {
				return Optional.of(RedenMachine.fromJson(element.getAsJsonObject()));
			}
		}

		return Optional.empty();
	}

	private static String encodeQuery(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String encodePathSegment(String value) {
		return encodeQuery(value).replace("+", "%20");
	}
}
