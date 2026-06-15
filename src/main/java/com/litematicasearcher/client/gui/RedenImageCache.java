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
 * 图片缓存
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

    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final long RETRY_DELAY_MS = 500L;

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

        // 检查缓存
        if (Files.exists(cached)) {
            registerFromCache(cached, hash, result, imageUrl);
            return result;
        }

        // 直接下载
        downloadImage(imageUrl, hash, cached, result, 0);
        return result;
    }

    private static void downloadImage(String imageUrl, String hash, Path cached,
                                      CompletableFuture<Identifier> result, int attempt) {
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
                        LitematicaSearcher.LOGGER.warn("图片下载失败 (尝试 {}/{}): {}: {}",
                                attempt + 1, MAX_RETRY_ATTEMPTS + 1, imageUrl, throwable.getMessage());
                        if (attempt < MAX_RETRY_ATTEMPTS) {
                            sleep(RETRY_DELAY_MS * (attempt + 1));
                            downloadImage(imageUrl, hash, cached, result, attempt + 1);
                        } else {
                            result.complete(null);
                        }
                        return;
                    }

                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        LitematicaSearcher.LOGGER.warn("图片下载失败 (尝试 {}/{}): HTTP {} - {}",
                                attempt + 1, MAX_RETRY_ATTEMPTS + 1, response.statusCode(), imageUrl);
                        if (attempt < MAX_RETRY_ATTEMPTS) {
                            sleep(RETRY_DELAY_MS * (attempt + 1));
                            downloadImage(imageUrl, hash, cached, result, attempt + 1);
                        } else {
                            result.complete(null);
                        }
                        return;
                    }

                    // 下载成功
                    byte[] body = response.body();
                    LitematicaSearcher.LOGGER.debug("图片下载成功: {} ({} 字节)", imageUrl, body.length);

                    try {
                        Files.createDirectories(cached.getParent());
                        Files.write(cached, body);
                    } catch (IOException e) {
                        LitematicaSearcher.LOGGER.warn("缓存图片失败: {}", e.getMessage());
                    }

                    registerFromBytes(body, hash, result, imageUrl);
                });
    }

    private static void registerFromCache(Path cached, String hash,
                                          CompletableFuture<Identifier> result, String imageUrl) {
        try {
            byte[] bytes = Files.readAllBytes(cached);
            registerFromBytes(bytes, hash, result, imageUrl);
        } catch (IOException e) {
            LitematicaSearcher.LOGGER.warn("读取缓存图片失败: {}", imageUrl);
            try {
                Files.deleteIfExists(cached);
            } catch (IOException ignored) {
            }
            result.complete(null);
        }
    }

    private static void registerFromBytes(byte[] bytes, String hash,
                                          CompletableFuture<Identifier> result, String imageUrl) {
        com.mojang.blaze3d.platform.NativeImage image = decodeAnyFormat(bytes);
        if (image == null) {
            LitematicaSearcher.LOGGER.warn("解码图片失败: {} ({} 字节)", imageUrl, bytes.length);
            result.complete(null);
            return;
        }

        Minecraft.getInstance().execute(() -> {
            try {
                String name = "reden_images/" + hash;
                Supplier<String> nameSupplier = () -> name;
                DynamicTexture texture = new DynamicTexture(nameSupplier, image);
                Identifier identifier = Identifier.fromNamespaceAndPath(LitematicaSearcher.MOD_ID, name);
                Minecraft.getInstance().getTextureManager().register(identifier, texture);
                result.complete(identifier);
                LitematicaSearcher.LOGGER.debug("纹理注册成功: {} -> {}", imageUrl, identifier);
            } catch (Exception e) {
                LitematicaSearcher.LOGGER.warn("注册纹理失败: {}: {}", imageUrl, e.getMessage());
                result.complete(null);
            }
        });
    }

    private static String sniffKind(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return "unknown";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "png";
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "gif";
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F') {
            return "webp";
        }
        return "unknown";
    }

    private static com.mojang.blaze3d.platform.NativeImage decodeAnyFormat(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        String kind = sniffKind(bytes);
        try {
            if ("png".equals(kind)) {
                return com.mojang.blaze3d.platform.NativeImage.read(new ByteArrayInputStream(bytes));
            }
            if ("jpeg".equals(kind) || "gif".equals(kind) || "webp".equals(kind)) {
                BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
                return src == null ? null : toNativeImage(src);
            }
            // 未知格式，尝试 NativeImage 读取，失败则尝试 ImageIO
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
                // setPixelABGR 确保颜色正确
                image.setPixelABGR(x, y, src.getRGB(x, y));
            }
        }
        return image;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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