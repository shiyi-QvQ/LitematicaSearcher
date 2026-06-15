package com.litematicasearcher.client.download;

import com.litematicasearcher.LitematicaSearcher;
import com.litematicasearcher.client.api.RedenAttachment;
import com.litematicasearcher.client.api.RedenMachine;
import com.litematicasearcher.client.config.RedenConfig;
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
import java.util.function.LongConsumer;

/**
 * Download manager that supports two endpoints (modern litematica/share + legacy yisibite).
 * Behavior is fully compatible with the original implementation; new config flag
 * {@code RedenConfig.compatibleDownload} controls whether to try the legacy yisibite endpoint
 * on first failure (default true).
 */
public final class RedenDownloadManager {
	// Two endpoints, prefer the dedicated share endpoint
	private static final String SHARE_BASE_URL = "https://redenmc.com/api/mc-services/litematica/share";
	private static final String YISIBITE_BASE_URL = "https://redenmc.com/api/mc-services/yisibite";
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private static final int MAX_ATTEMPTS = 3;
	private static final long BACKOFF_BASE_MS = 1000L;

	private RedenDownloadManager() {
	}

	public static CompletableFuture<Path> downloadAttachment(RedenMachine machine, int attachmentIndex) {
		if (attachmentIndex < 0 || attachmentIndex >= machine.attachments().size()) {
			throw new IllegalArgumentException("attachmentIndex is out of range");
		}

		RedenAttachment attachment = machine.attachments().get(attachmentIndex);
		String fallbackName = attachment.name().isBlank()
				? safeBaseName(machine.name()) + "-" + (attachmentIndex + 1) + ".litematic"
				: attachment.name();
		return downloadWithProgress(machine.key(), attachmentIndex + 1, fallbackName, null);
	}

	public static CompletableFuture<Path> downloadGenerated(RedenMachine machine, Map<String, Integer> sizes) {
		StringBuilder url = new StringBuilder(YISIBITE_BASE_URL).append("/").append(encode(machine.key()));
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

		return downloadWithProgress(URI.create(url.toString()), safeBaseName(machine.name()) + "-generated.litematic", null);
	}

	public static CompletableFuture<Path> downloadAttachmentWithProgress(RedenMachine machine, int attachmentIndex, LongConsumer progressListener) {
		if (attachmentIndex < 0 || attachmentIndex >= machine.attachments().size()) {
			throw new IllegalArgumentException("attachmentIndex is out of range");
		}

		RedenAttachment attachment = machine.attachments().get(attachmentIndex);
		String fallbackName = attachment.name().isBlank()
				? safeBaseName(machine.name()) + "-" + (attachmentIndex + 1) + ".litematic"
				: attachment.name();
		return downloadWithProgress(machine.key(), attachmentIndex + 1, fallbackName, progressListener);
	}

	private static CompletableFuture<Path> downloadWithProgress(String machineKey, int attachmentNumber, String fallbackName, LongConsumer listener) {
		URI shareUri = URI.create(SHARE_BASE_URL + "/" + encode(machineKey) + "/download");
		return downloadWithProgress(shareUri, machineKey, fallbackName, listener);
	}

	private static CompletableFuture<Path> downloadWithProgress(URI primaryUri, String fallbackFileName, LongConsumer progressListener) {
		String machineKey = extractKey(primaryUri.toString());
		return downloadWithProgress(primaryUri, machineKey, fallbackFileName, progressListener);
	}

	private static CompletableFuture<Path> downloadWithProgress(URI primaryUri, String machineKey, String fallbackFileName, LongConsumer progressListener) {
		CompletableFuture<Path> chain = new CompletableFuture<>();
		attempt(primaryUri, machineKey, fallbackFileName, progressListener, 1, chain);
		return chain;
	}

	private static void attempt(URI uri, String machineKey, String fallbackFileName, LongConsumer progressListener, int attemptNo, CompletableFuture<Path> chain) {
		String referer = machineKey == null || machineKey.isBlank()
				? "https://redenmc.com/zh_cn/litematica"
				: "https://redenmc.com/zh_cn/litematica/" + machineKey;

		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(60))
				.header("User-Agent", USER_AGENT)
				.header("Accept", "*/*")
				.header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
				.header("Referer", referer)
				.GET()
				.build();

		HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
				.whenComplete((response, throwable) -> {
					if (throwable != null) {
						if (attemptNo < MAX_ATTEMPTS) {
							sleep(BACKOFF_BASE_MS * (1L << (attemptNo - 1)));
							attempt(uri, machineKey, fallbackFileName, progressListener, attemptNo + 1, chain);
						} else {
							chain.completeExceptionally(new DownloadException("Network error after " + MAX_ATTEMPTS + " attempts: " + throwable.getMessage(), throwable));
						}
						return;
					}

					int status = response.statusCode();

					if (status < 200 || status >= 300) {
						// First failure on primary endpoint, try the alternate yisibite endpoint
						// (legacy-compatible fallback; controlled by RedenConfig.compatibleDownload)
						if (attemptNo == 1
								&& RedenConfig.get().compatibleDownload()
								&& machineKey != null
								&& !machineKey.isBlank()
								&& uri.toString().contains("/litematica/share/")) {
							URI fallbackUri = URI.create(YISIBITE_BASE_URL + "/" + encode(machineKey));
							LitematicaSearcher.LOGGER.info("Primary share endpoint returned HTTP {}, falling back to yisibite endpoint for key {}", status, machineKey);
							attempt(fallbackUri, machineKey, fallbackFileName, progressListener, attemptNo + 1, chain);
							return;
						}
						if (attemptNo < MAX_ATTEMPTS) {
							sleep(BACKOFF_BASE_MS * (1L << (attemptNo - 1)));
							attempt(uri, machineKey, fallbackFileName, progressListener, attemptNo + 1, chain);
						} else {
							chain.completeExceptionally(new DownloadException("RedenMC download returned HTTP " + status));
						}
						return;
					}

					byte[] body = response.body();

					if (progressListener != null) {
						try {
							progressListener.accept(body.length);
						} catch (RuntimeException ignored) {
						}
					}

					try {
						String fileName = response.headers()
								.firstValue("content-disposition")
								.flatMap(RedenDownloadManager::fileNameFromContentDisposition)
								.orElse(fallbackFileName);

						Path directory = downloadsDirectory();
						Files.createDirectories(directory);
						Path target = uniqueTarget(directory, sanitizeFileName(fileName));
						Files.write(target, body);
						chain.complete(target);
					} catch (IOException exception) {
						chain.completeExceptionally(new DownloadException("Failed to save RedenMC download", exception));
					}
				});
	}

	private static String extractKey(String uri) {
		int idx = uri.indexOf("/litematica/");
		if (idx < 0) {
			idx = uri.indexOf("/yisibite/");
		}
		if (idx < 0) {
			return null;
		}
		String tail = uri.substring(idx);
		String[] parts = tail.split("/");
		return parts.length >= 3 ? parts[2] : null;
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
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
				String raw = stripQuotes(trimmed.substring("filename=".length()));
				return Optional.of(recoverUtf8FileName(raw));
			}
		}

		return Optional.empty();
	}

	private static String recoverUtf8FileName(String raw) {
		try {
			byte[] bytes = raw.getBytes(StandardCharsets.ISO_8859_1);
			String recovered = new String(bytes, StandardCharsets.UTF_8);

			if (!recovered.equals(raw) && recovered.length() > 0) {
				return recovered;
			}
		} catch (Exception ignored) {
		}

		return raw;
	}

	private static String sanitizeFileName(String fileName) {
		String sanitized = fileName
				.replaceAll("[<>\\\"/\\\\|?*\\p{Cntrl}]", "_")
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
