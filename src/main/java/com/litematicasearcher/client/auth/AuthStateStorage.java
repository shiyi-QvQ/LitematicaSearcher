package com.litematicasearcher.client.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.litematicasearcher.LitematicaSearcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 设备流认证 - 完整实现
 * 包含：数据模型、API 客户端、状态持久化
 */
public final class AuthStateStorage {

    // ==================== Gson 和 HTTP 客户端 ====================
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ==================== API 配置 ====================
    // TODO: 替换为实际的客户端 ID 和密钥
    private static final String CLIENT_ID = "";
    private static final String AUTH_KEY = "";
    private static final String BASE_URL = "https://redenmc.com/api/auth/device";
    private static final String REDIRECT_URL = "https://redenmc.com/auth/device";

    // ==================== 存储路径 ====================
    private static final String FILE_NAME = "auth_state.json";
    private static final Path STORAGE_PATH = Paths.get(
            LitematicaSearcher.getConfigDirectory().getAbsolutePath(),
            FILE_NAME
    );

    private static AuthState instance;

    // ==================== 数据模型 ====================

    /**
     * 认证状态
     */
    public static class AuthState {
        // 登录信息
        private String accessToken = "";
        private String refreshToken = "";
        private long expiresAt = 0;
        private long userId = 0;
        private String username = "";
        private boolean isLoggedIn = false;

        // 设备流信息
        private String deviceCode = "";
        private String queryToken = "";
        private long deviceExpiresAt = 0;

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

        public long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

        public long getUserId() { return userId; }
        public void setUserId(long userId) { this.userId = userId; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public boolean isLoggedIn() { return isLoggedIn; }
        public void setLoggedIn(boolean loggedIn) { isLoggedIn = loggedIn; }

        public String getDeviceCode() { return deviceCode; }
        public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }

        public String getQueryToken() { return queryToken; }
        public void setQueryToken(String queryToken) { this.queryToken = queryToken; }

        public long getDeviceExpiresAt() { return deviceExpiresAt; }
        public void setDeviceExpiresAt(long deviceExpiresAt) { this.deviceExpiresAt = deviceExpiresAt; }

        public boolean isAccessTokenValid() {
            return isLoggedIn && !accessToken.isEmpty() && System.currentTimeMillis() < expiresAt;
        }

        public boolean isDeviceValid() {
            return !deviceCode.isEmpty() && !queryToken.isEmpty() && System.currentTimeMillis() < deviceExpiresAt;
        }

        public void clear() {
            accessToken = "";
            refreshToken = "";
            expiresAt = 0;
            userId = 0;
            username = "";
            isLoggedIn = false;
            deviceCode = "";
            queryToken = "";
            deviceExpiresAt = 0;
        }

        public void clearDeviceInfo() {
            deviceCode = "";
            queryToken = "";
            deviceExpiresAt = 0;
        }
    }

    /**
     * 启动认证响应
     */
    public static class StartResponse {
        @SerializedName("redirect_url")
        private String redirectUrl;

        @SerializedName("query_token")
        private String queryToken;

        @SerializedName("expires_in")
        private int expiresIn;

        public String getRedirectUrl() { return redirectUrl; }
        public String getQueryToken() { return queryToken; }
        public int getExpiresIn() { return expiresIn; }
    }

    /**
     * 查询认证状态响应
     */
    public static class QueryResponse {
        private String status;

        @SerializedName("user_id")
        private long userId;

        private String username;

        @SerializedName("access_token")
        private String accessToken;

        @SerializedName("token_type")
        private String tokenType;

        @SerializedName("expires_in")
        private long expiresIn;

        public String getStatus() { return status; }
        public long getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getAccessToken() { return accessToken; }
        public String getTokenType() { return tokenType; }
        public long getExpiresIn() { return expiresIn; }

        public boolean isPending() { return "pending".equals(status); }
        public boolean isApproved() { return "approved".equals(status); }
        public boolean isDenied() { return "denied".equals(status); }
        public boolean isExpired() { return "expired".equals(status); }
    }

    /**
     * 错误响应
     */
    public static class ErrorResponse {
        private String error;
        public String getError() { return error; }
    }

    // ==================== 状态持久化 ====================

    public static AuthState getState() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static AuthState load() {
        try {
            if (Files.exists(STORAGE_PATH)) {
                String json = Files.readString(STORAGE_PATH);
                AuthState state = GSON.fromJson(json, AuthState.class);
                if (state != null) {
                    return state;
                }
            }
        } catch (IOException e) {
            LitematicaSearcher.LOGGER.error("Failed to load auth state", e);
        }
        return new AuthState();
    }

    public static void save() {
        if (instance == null) {
            return;
        }
        try {
            Files.createDirectories(STORAGE_PATH.getParent());
            String json = GSON.toJson(instance);
            Files.writeString(STORAGE_PATH, json);
        } catch (IOException e) {
            LitematicaSearcher.LOGGER.error("Failed to save auth state", e);
        }
    }

    public static void updateLoginState(String accessToken, String refreshToken, long expiresIn,
                                        long userId, String username) {
        AuthState state = getState();
        state.setAccessToken(accessToken);
        state.setRefreshToken(refreshToken);
        state.setExpiresAt(System.currentTimeMillis() + expiresIn * 1000);
        state.setUserId(userId);
        state.setUsername(username);
        state.setLoggedIn(true);
        state.clearDeviceInfo();
        save();
    }

    public static void updateDeviceState(String deviceCode, String queryToken, int expiresIn) {
        AuthState state = getState();
        state.setDeviceCode(deviceCode);
        state.setQueryToken(queryToken);
        state.setDeviceExpiresAt(System.currentTimeMillis() + expiresIn * 1000L);
        save();
    }

    public static void logout() {
        AuthState state = getState();
        state.clear();
        save();
    }

    // ==================== API 调用 ====================

    /**
     * 启动设备流认证
     */
    public static StartResponse startDeviceFlow() throws IOException, InterruptedException {
        String requestBody = String.format(
                "{\"client_id\":\"%s\",\"auth_key\":\"%s\",\"redirect_url\":\"%s\"}",
                CLIENT_ID, AUTH_KEY, REDIRECT_URL
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/start"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return GSON.fromJson(response.body(), StartResponse.class);
        } else {
            ErrorResponse error = GSON.fromJson(response.body(), ErrorResponse.class);
            throw new IOException("启动认证失败: " + (error != null ? error.getError() : "未知错误") +
                    " (HTTP " + response.statusCode() + ")");
        }
    }

    /**
     * 查询认证状态
     */
    public static QueryResponse queryAuthStatus(String queryToken)
            throws IOException, InterruptedException {

        String requestBody = String.format("{\"query_token\":\"%s\"}", queryToken);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/query"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return GSON.fromJson(response.body(), QueryResponse.class);
        } else if (response.statusCode() == 410) {
            QueryResponse expiredResponse = new QueryResponse();
            // 通过反射或手动设置 status
            try {
                var field = QueryResponse.class.getDeclaredField("status");
                field.setAccessible(true);
                field.set(expiredResponse, "expired");
            } catch (Exception ignored) {}
            return expiredResponse;
        } else {
            ErrorResponse error = GSON.fromJson(response.body(), ErrorResponse.class);
            throw new IOException("查询认证状态失败: " + (error != null ? error.getError() : "未知错误") +
                    " (HTTP " + response.statusCode() + ")");
        }
    }

    /**
     * 获取认证信息（可选）
     */
    public static String getAuthInfo(String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/info?token=" + token))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new IOException("获取认证信息失败: HTTP " + response.statusCode());
        }
    }

    // ==================== 便捷方法 ====================

    /**
     * 检查是否已登录
     */
    public static boolean isLoggedIn() {
        return getState().isAccessTokenValid();
    }

    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        return getState().getUsername();
    }

    /**
     * 获取 Access Token
     */
    public static String getAccessToken() {
        return getState().getAccessToken();
    }

    /**
     * 获取用户 ID
     */
    public static long getUserId() {
        return getState().getUserId();
    }
}