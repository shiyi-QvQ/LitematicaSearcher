package com.litematicasearcher.client.download;

import com.litematicasearcher.client.api.RedenAttachment;
import com.litematicasearcher.client.api.RedenMachine;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class RedenDownloadManager {
	private static final String BASE_URL = "https://redenmc.com/api/mc-services/yisibite";
	private static final String USER_AGENT = "LitematicaSearcher/1.0.0 Minecraft-Fabric";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private RedenDownloadManager() {
	}

	public static CompletableFuture<Path> downloadAttachment(RedenMachine machine, int attachmentIndex) {
		if (attachmentIndex < 0 || attachmentIndex >= machine.attachments().size()) {
			throw new IllegalArgumentException("attachmentIndex is out of range");
		}

		RedenAttachment attachment = machine.attachments().get(attachmentIndex);
		URI uri = URI.create(BASE_URL + "/" + encode(machine.key()) + "/download/" + (attachmentIndex + 1));
		String fallbackName = attachment.name().isBlank()
				? safeBaseName(machine.name()) + "-" + (attachmentIndex + 1) + ".litematic"
				: attachment.name();
		return download(uri, fallbackName);
	}

	public static CompletableFuture<Path> downloadGenerated(RedenMachine machine, Map<String, Integer> sizes) {
		StringBuilder url = new StringBuilder(BASE_URL).append("/").append(encode(machine.key()));
		boolean first = true;

		for (String axis : new String[]{"x", "y", "z"}) {
			Integer value = sizes.get(axis);

			if (value != null) {
				url.append(first ? "?" : "&")
						.append(axis)
						.append("Size=")
						.append(value);
				first = false;
			}
		}

		return download(URI.create(url.toString()), safeBaseName(machine.name()) + "-generated.litematic");
	}

	private static CompletableFuture<Path> download(URI uri, String fallbackFileName) {
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(60))
				.header("User-Agent", USER_AGENT)
				.GET()
				.build();

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
				.thenApply(response -> saveResponse(response, fallbackFileName));
	}

	private static Path saveResponse(HttpResponse<byte[]> response, String fallbackFileName) {
		int status = response.statusCode();

		if (status < 200 || status >= 300) {
			throw new DownloadException("RedenMC download returned HTTP " + status);
		}

		String fileName = response.headers()
				.firstValue("content-disposition")
				.flatMap(RedenDownloadManager::fileNameFromContentDisposition)
				.orElse(fallbackFileName);

		try {
			Path directory = downloadsDirectory();
			Files.createDirectories(directory);
			Path target = uniqueTarget(directory, sanitizeFileName(fileName));
			Files.write(target, response.body());
			return target;
		} catch (IOException exception) {
			throw new DownloadException("Failed to save RedenMC download", exception);
		}
	}

	private static Path downloadsDirectory() {
		return Minecraft.getInstance()
				.gameDirectory
				.toPath()
				.resolve("schematics")
				.resolve("Downloads");
	}

	private static Path uniqueTarget(Path directory, String fileName) {
		Path target = directory.resolve(fileName);

		if (!Files.exists(target)) {
			return target;
		}

		String baseName = fileName;
		String extension = "";
		int dot = fileName.lastIndexOf('.');

		if (dot > 0) {
			baseName = fileName.substring(0, dot);
			extension = fileName.substring(dot);
		}

		for (int index = 1; index < 10_000; index++) {
			Path candidate = directory.resolve(baseName + " (" + index + ")" + extension);

			if (!Files.exists(candidate)) {
				return candidate;
			}
		}

		throw new DownloadException("Could not choose a unique filename for " + fileName);
	}

	private static Optional<String> fileNameFromContentDisposition(String header) {
		for (String part : header.split(";")) {
			String trimmed = part.trim();

			if (trimmed.startsWith("filename*=")) {
				String value = trimmed.substring("filename*=".length());
				int separator = value.indexOf("''");

				if (separator >= 0) {
					value = value.substring(separator + 2);
				}

				return Optional.of(URLDecoder.decode(stripQuotes(value), StandardCharsets.UTF_8));
			}

			if (trimmed.startsWith("filename=")) {
				return Optional.of(stripQuotes(trimmed.substring("filename=".length())));
			}
		}

		return Optional.empty();
	}

	private static String sanitizeFileName(String fileName) {
		String sanitized = fileName
				.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_")
				.trim();

		if (sanitized.isBlank()) {
			return "redenmc-download.litematic";
		}

		return sanitized;
	}

	private static String safeBaseName(String value) {
		return sanitizeFileName(value).replaceAll("\\.litematic$", "");
	}

	private static String stripQuotes(String value) {
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			return value.substring(1, value.length() - 1);
		}

		return value;
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	public static final class DownloadException extends RuntimeException {
		public DownloadException(String message) {
			super(message);
		}

		public DownloadException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
