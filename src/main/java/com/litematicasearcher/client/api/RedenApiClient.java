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
	public static final int DEFAULT_PAGE_SIZE = 10;
	public static final int DEFAULT_PAGE = 1;

	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
	private static final String BASE_URL = "https://redenmc.com/api/mc-services";
	private static final String API_REFERER = "https://redenmc.com/zh_cn/litematica";
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
		return search(query, DEFAULT_PAGE, DEFAULT_PAGE_SIZE);
	}

	public CompletableFuture<RedenSearchResponse> searchByPage(String query, int page, int pageSize) {
		return search(query, page, pageSize);
	}

	public CompletableFuture<RedenSearchResponse> search(String query, int page, int pageSize) {
		if (page < 1) {
			page = 1;
		}

		if (pageSize <= 0) {
			throw new IllegalArgumentException("pageSize must be greater than 0");
		}

		String normalizedQuery = query == null ? "" : query;
		URI uri = URI.create(BASE_URL + "/litematica/search?q=" + encodeQuery(normalizedQuery)
				+ "&lang=zh_cn"
				+ "&page=" + page
				+ "&pageSize=" + pageSize);

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
				.header("Referer", API_REFERER)
				.header("Accept-Language", "zh-CN,zh;q=0.9")
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
