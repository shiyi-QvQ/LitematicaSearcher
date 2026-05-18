package com.litematicasearcher.client.gui;

import com.litematicasearcher.LitematicaSearcher;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class RedenImageCache {
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private static final Map<String, CompletableFuture<Identifier>> TEXTURES = new ConcurrentHashMap<>();
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

	private RedenImageCache() {
	}

	static Identifier textureFor(String imageUrl) {
		if (imageUrl == null || imageUrl.isBlank()) {
			return null;
		}

		CompletableFuture<Identifier> future = TEXTURES.computeIfAbsent(imageUrl, RedenImageCache::loadTexture);

		if (!future.isDone() || future.isCompletedExceptionally()) {
			return null;
		}

		return future.getNow(null);
	}

	private static CompletableFuture<Identifier> loadTexture(String imageUrl) {
		CompletableFuture<Identifier> result = new CompletableFuture<>();
		String hash = sha1(imageUrl);
		Path cached = imageCachePath(hash);

		if (Files.exists(cached)) {
			registerFromCache(cached, hash, result, imageUrl);
			return result;
		}

		HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", USER_AGENT)
				.header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
				.header("Referer", "https://redenmc.com/")
				.GET()
				.build();

		HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
				.whenComplete((response, throwable) -> {
					if (throwable != null) {
						LitematicaSearcher.LOGGER.warn("Failed to download RedenMC image {}: {}", imageUrl, throwable.getMessage());
						registerPlaceholder(result, imageUrl);
						return;
					}

					if (response.statusCode() < 200 || response.statusCode() >= 300) {
						LitematicaSearcher.LOGGER.warn("RedenMC image {} returned HTTP {} (content-type: {})", imageUrl, response.statusCode(), response.headers().firstValue("content-type").orElse("?"));
						registerPlaceholder(result, imageUrl);
						return;
					}

					byte[] body = response.body();
					LitematicaSearcher.LOGGER.debug("RedenMC image {} downloaded {} bytes", imageUrl, body.length);

					try {
						Files.createDirectories(cached.getParent());
						Files.write(cached, body);
					} catch (IOException e) {
						LitematicaSearcher.LOGGER.warn("Failed to cache image {}: {}", imageUrl, e.getMessage());
					}

					registerFromBytes(body, hash, result, imageUrl);
				});

		return result;
	}

	private static void registerFromCache(Path cached, String hash, CompletableFuture<Identifier> result, String imageUrl) {
		try {
			byte[] bytes = Files.readAllBytes(cached);
			registerFromBytes(bytes, hash, result, imageUrl);
		} catch (IOException e) {
			LitematicaSearcher.LOGGER.warn("Failed to read cached image {}: {}", imageUrl, e.getMessage());
			try {
				Files.deleteIfExists(cached);
			} catch (IOException ignored) {
			}
			result.complete(null);
		}
	}

	private static void registerFromBytes(byte[] bytes, String hash, CompletableFuture<Identifier> result, String imageUrl) {
		NativeImage image;

		try {
			image = NativeImage.read(new ByteArrayInputStream(bytes));
		} catch (Exception exception) {
			LitematicaSearcher.LOGGER.warn("Failed to decode RedenMC image {} ({} bytes, starts with {:02x}{:02x}): {}", imageUrl, bytes.length, bytes.length > 0 ? bytes[0] : 0, bytes.length > 1 ? bytes[1] : 0, exception.getMessage());
			registerPlaceholder(result, imageUrl);
			return;
		}

		Minecraft.getInstance().execute(() -> {
			try {
				Identifier identifier = Identifier.fromNamespaceAndPath(LitematicaSearcher.MOD_ID, "reden_images/" + hash);
				Minecraft.getInstance().getTextureManager().register(identifier, new DynamicTexture(() -> "RedenMC image " + hash, image));
				result.complete(identifier);
				LitematicaSearcher.LOGGER.debug("Registered texture {} for {}", identifier, imageUrl);
			} catch (Exception e) {
				LitematicaSearcher.LOGGER.warn("Failed to register texture for {}: {}", imageUrl, e.getMessage());
				result.complete(null);
			}
		});
	}

	private static void registerPlaceholder(CompletableFuture<Identifier> result, String imageUrl) {
		Minecraft.getInstance().execute(() -> {
			LitematicaSearcher.LOGGER.debug("Could not load image {}, using fallback", imageUrl);
			result.complete(null);
		});
	}

	private static Path imageCachePath(String hash) {
		return Minecraft.getInstance()
				.gameDirectory
				.toPath()
				.resolve("litematicasearcher")
				.resolve("image_cache")
				.resolve(hash + ".png");
	}

	private static String sha1(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			return Integer.toUnsignedString(value.hashCode(), 16);
		}
	}
}
