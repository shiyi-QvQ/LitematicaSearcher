package com.litematicasearcher.client.gui;

import com.litematicasearcher.LitematicaSearcher;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
	private static final String USER_AGENT = "LitematicaSearcher/1.0.0 Minecraft-Fabric";

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
		HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", USER_AGENT)
				.GET()
				.build();

		HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
				.whenComplete((response, throwable) -> {
					if (throwable != null) {
						LitematicaSearcher.LOGGER.warn("Failed to load RedenMC image {}", imageUrl, throwable);
						result.complete(null);
						return;
					}

					if (response.statusCode() < 200 || response.statusCode() >= 300) {
						LitematicaSearcher.LOGGER.warn("RedenMC image {} returned HTTP {}", imageUrl, response.statusCode());
						result.complete(null);
						return;
					}

					NativeImage image;

					try {
						image = NativeImage.read(new ByteArrayInputStream(response.body()));
					} catch (Exception exception) {
						LitematicaSearcher.LOGGER.warn("Failed to decode RedenMC image {}", imageUrl, exception);
						result.complete(null);
						return;
					}

					Minecraft.getInstance().execute(() -> {
						Identifier identifier = Identifier.fromNamespaceAndPath(
								LitematicaSearcher.MOD_ID,
								"reden_images/" + sha1(imageUrl)
						);
						Minecraft.getInstance().getTextureManager().register(
								identifier,
								new DynamicTexture(() -> "RedenMC image " + imageUrl, image)
						);
						result.complete(identifier);
					});
				});

		return result;
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
