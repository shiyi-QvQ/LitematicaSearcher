package com.litematicasearcher.client.gui;

import com.litematicasearcher.LitematicaSearcher;
import com.litematicasearcher.client.config.RedenConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
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
import java.util.function.Supplier;

/**
 * Image cache with three-tier fallback to survive redenmc anti-bot:
 *   1. Direct connection with correct anti-bot headers
 *   2. Public CORS-friendly image proxy (drops Referer)
 *   3. Async retry on direct (3 attempts, exponential backoff)
 *
 * redenmc.com mixes PNG and JPEG attachments. com.mojang.blaze3d.platform.NativeImage.read
 * handles PNG natively. For JPEG/GIF/WebP we go through ImageIO -> BufferedImage, then
 * copy pixels into a fresh NativeImage via setPixelRGBA (1.21.11 still exists) — setPixel* was removed
 * in 1.21+ for 3-arg form, but setPixelRGBA is still available and takes native
 * 0xAARRGGBB which matches BufferedImage.getRGB() output.
 *
 * In Mojang official 1.21.11 mappings, NativeImageBackedTexture was merged into
 * DynamicTexture (a concrete class with a `pixels` field, not an interface), with a
 * constructor (Supplier<String> nameSupplier, NativeImage pixels).
 */
final class RedenImageCache {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Map<String, CompletableFuture<Identifier>> TEXTURES = new ConcurrentHashMap<>();

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final String IMAGE_REFERER = "https://redenmc.com/";
    private static final String IMAGE_ACCEPT = "image/webp,image/apng,image/*,*/*;q=0.8";

    private static final String[] PROXY_HOSTS = {
            "https://images.weserv.nl/?url=",
            "https://wsrv.nl/?url="
    };

    private static final int MAX_DIRECT_ATTEMPTS = 3;
    private static final long BACKOFF_BASE_MS = 600L;

    private RedenImageCache() {
    }

    static Identifier textureFor(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        CompletableFuture<Identifier> future = TEXTURES.computeIfAbsent(imageUrl, RedenImageCache::loadTexture);
        if (!future.isDone() || future.isCompletedExceptionally()) return null;
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

        RedenConfig.ImageSource mode = RedenConfig.get().imageSource();
        switch (mode) {
            case DIRECT -> tryDirect(imageUrl, hash, cached, result, 1);
            case PROXY -> tryProxy(imageUrl, hash, cached, result, 0);
            case AUTO -> {
                tryDirect(imageUrl, hash, cached, result, 1)
                        .whenComplete((ok, ex) -> {
                            if (ok == null || ex != null || Boolean.FALSE.equals(ok)) {
                                LitematicaSearcher.LOGGER.info("Direct image load failed for {}, falling back to proxy", imageUrl);
                                tryProxy(imageUrl, hash, cached, result, 0);
                            }
                        });
            }
        }
        return result;
    }

    private static CompletableFuture<Boolean> tryDirect(String imageUrl, String hash, Path cached,
                                                       CompletableFuture<Identifier> result, int attemptNo) {
        CompletableFuture<Boolean> chain = new CompletableFuture<>();
        HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", IMAGE_ACCEPT)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Referer", IMAGE_REFERER)
                .GET()
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        LitematicaSearcher.LOGGER.warn("RedenMC direct image attempt {} failed for {}: {}",
                                attemptNo, imageUrl, throwable.getMessage());
                        if (attemptNo < MAX_DIRECT_ATTEMPTS) {
                            sleep(BACKOFF_BASE_MS * (1L << (attemptNo - 1)));
                            tryDirect(imageUrl, hash, cached, result, attemptNo + 1)
                                    .whenComplete((v, e) -> chain.complete(v));
                        } else {
                            chain.complete(Boolean.FALSE);
                        }
                        return;
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        LitematicaSearcher.LOGGER.warn("RedenMC direct image attempt {} got HTTP {} for {}",
                                attemptNo, response.statusCode(), imageUrl);
                        if (attemptNo < MAX_DIRECT_ATTEMPTS) {
                            sleep(BACKOFF_BASE_MS * (1L << (attemptNo - 1)));
                            tryDirect(imageUrl, hash, cached, result, attemptNo + 1)
                                    .whenComplete((v, e) -> chain.complete(v));
                        } else {
                            chain.complete(Boolean.FALSE);
                        }
                        return;
                    }
                    handleSuccess(response.body(), hash, cached, imageUrl, result);
                    chain.complete(Boolean.TRUE);
                });
        return chain;
    }

    private static void tryProxy(String imageUrl, String hash, Path cached,
                                 CompletableFuture<Identifier> result, int proxyIndex) {
        if (proxyIndex >= PROXY_HOSTS.length) {
            LitematicaSearcher.LOGGER.warn("All proxy fallbacks failed for {}", imageUrl);
            result.complete(null);
            return;
        }
        String encoded = URLEncoder.encode(imageUrl, StandardCharsets.UTF_8);
        URI proxyUri = URI.create(PROXY_HOSTS[proxyIndex] + encoded);
        HttpRequest request = HttpRequest.newBuilder(proxyUri)
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", USER_AGENT)
                .header("Accept", IMAGE_ACCEPT)
                .GET()
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        LitematicaSearcher.LOGGER.warn("Proxy {} failed for {}: {}", PROXY_HOSTS[proxyIndex], imageUrl, throwable.getMessage());
                        tryProxy(imageUrl, hash, cached, result, proxyIndex + 1);
                        return;
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        LitematicaSearcher.LOGGER.warn("Proxy {} got HTTP {} for {}", PROXY_HOSTS[proxyIndex], response.statusCode(), imageUrl);
                        tryProxy(imageUrl, hash, cached, result, proxyIndex + 1);
                        return;
                    }
                    handleSuccess(response.body(), hash, cached, imageUrl, result);
                });
    }

    private static void handleSuccess(byte[] body, String hash, Path cached,
                                      String imageUrl, CompletableFuture<Identifier> result) {
        LitematicaSearcher.LOGGER.debug("RedenMC image {} downloaded {} bytes", imageUrl, body.length);
        try {
            Files.createDirectories(cached.getParent());
            Files.write(cached, body);
        } catch (IOException e) {
            LitematicaSearcher.LOGGER.warn("Failed to cache image {}: {}", imageUrl, e.getMessage());
        }
        registerFromBytes(body, hash, result, imageUrl);
    }

    private static void registerFromCache(Path cached, String hash,
                                          CompletableFuture<Identifier> result, String imageUrl) {
        try {
            byte[] bytes = Files.readAllBytes(cached);
            registerFromBytes(bytes, hash, result, imageUrl);
        } catch (IOException e) {
            LitematicaSearcher.LOGGER.warn("Failed to read cached image {}: {}", imageUrl, e.getMessage());
            try { Files.deleteIfExists(cached); } catch (IOException ignored) {}
            result.complete(null);
        }
    }

    private static void registerFromBytes(byte[] bytes, String hash,
                                          CompletableFuture<Identifier> result, String imageUrl) {
        com.mojang.blaze3d.platform.NativeImage image = decodeAnyFormat(bytes);
        if (image == null) {
            String kind = sniffKind(bytes);
            LitematicaSearcher.LOGGER.warn("Failed to decode RedenMC image {} ({} bytes, format={})", imageUrl, bytes.length, kind);
            result.complete(null);
            return;
        }

        Minecraft.getInstance().execute(() -> {
            try {
                String name = "reden_images/" + hash;
                Supplier<String> nameSupplier = () -> name;
                // 1.21.11 Mojang official: DynamicTexture is a concrete class
                // that takes (Supplier<String> nameSupplier, NativeImage pixels).
                // (NativeImageBackedTexture was merged into DynamicTexture.)
                DynamicTexture texture = new DynamicTexture(nameSupplier, image);
                Identifier identifier = Identifier.fromNamespaceAndPath(LitematicaSearcher.MOD_ID, name);
                Minecraft.getInstance().getTextureManager().register(identifier, texture);
                result.complete(identifier);
                LitematicaSearcher.LOGGER.debug("Registered texture {} for {}", identifier, imageUrl);
            } catch (Exception e) {
                LitematicaSearcher.LOGGER.warn("Failed to register texture for {}: {}", imageUrl, e.getMessage());
                result.complete(null);
            }
        });
    }

    private static String sniffKind(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return "unknown";
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) return "jpeg";
        if (bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "png";
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return "gif";
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F') return "webp";
        return "unknown";
    }

    private static com.mojang.blaze3d.platform.NativeImage decodeAnyFormat(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return null;
        String kind = sniffKind(bytes);
        try {
            if ("png".equals(kind)) {
                return com.mojang.blaze3d.platform.NativeImage.read(new ByteArrayInputStream(bytes));
            }
            if ("jpeg".equals(kind) || "gif".equals(kind) || "webp".equals(kind)) {
                BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
                return src == null ? null : toNativeImage(src);
            }
            try {
                return com.mojang.blaze3d.platform.NativeImage.read(new ByteArrayInputStream(bytes));
            } catch (Exception ignored) {
                BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
                return src == null ? null : toNativeImage(src);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static com.mojang.blaze3d.platform.NativeImage toNativeImage(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        com.mojang.blaze3d.platform.NativeImage image =
                new com.mojang.blaze3d.platform.NativeImage(
                        com.mojang.blaze3d.platform.NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // 1.21.11 NativeImage has two 3-int pixel writers:
                //   setPixelRGBA(int, int, int) -> memPutInt(0xAARRGGBB) — byte order is RGBA in big-endian,
                //                                       but memory is little-endian, so OpenGL reads R=B, B=R (red/blue swapped).
                //   setPixelABGR(int, int, int) -> internally reshuffles to 0xAABBGGRR before memPutInt,
                //                                       so OpenGL reads R and B correctly.
                // We use setPixelABGR so that BufferedImage.getRGB() (0xAARRGGBB) maps to the correct on-GPU colors.
                image.setPixelABGR(x, y, src.getRGB(x, y));
            }
        }
        return image;
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
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
