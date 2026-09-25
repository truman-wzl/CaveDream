package com.cavedream.core.net;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 云端认证 API 客户端：注册 / 登录 / 找回密码。
 * 游戏侧永不抛异常阻塞主流程——一切失败以 ok=false + message 返回，由 UI 展示。
 * 服务端提示语为英文（默认位图字体无中文字形，中文字体 M3 随设置界面接入）。
 */
public class CloudAuthClient {

    /** 一次认证调用的结果。登录成功时携带 token 与昵称。 */
    public record Result(boolean ok, String message, String token, String nickname) {

        static Result fail(String msg) {
            return new Result(false, msg, null, null);
        }
    }

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public CloudAuthClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public Result sendCode(String email) {
        return post("/api/auth/send-code", Map.of("email", email));
    }

    public Result register(String email, String username, String code, String password, String nickname) {
        return post("/api/auth/register", Map.of(
                "email", email,
                "username", username,
                "code", code,
                "password", password,
                "nickname", nickname));
    }

    /** 用户中心：登录态下改昵称（凭 token）。 */
    public Result changeNickname(String token, String nickname) {
        return postAuth("/api/auth/nickname", Map.of("nickname", nickname), token);
    }

    /** 登出（吊销服务端会话）。 */
    public Result logout(String token) {
        return postAuth("/api/auth/logout", Map.of(), token);
    }

    /** 会话校验：token 是否仍有效（ok=有效）。 */
    public Result me(String token) {
        return postAuth("/api/auth/me", Map.of(), token);
    }

    public Result login(String account, String password) {
        return post("/api/auth/login", Map.of(
                "account", account,
                "password", password));
    }

    public Result resetPassword(String email, String code, String newPassword) {
        return post("/api/auth/reset", Map.of(
                "email", email,
                "code", code,
                "newPassword", newPassword));
    }

    @SuppressWarnings("unchecked")
    private Result post(String path, Map<String, Object> body) {
        return postAuth(path, body, null);
    }

    @SuppressWarnings("unchecked")
    private Result postAuth(String path, Map<String, Object> body, String token) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (token != null && !token.isBlank()) {
                builder.header("X-Token", token);
            }
            HttpResponse<String> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> map = resp.body() == null || resp.body().isBlank()
                    ? Map.of() : json.readValue(resp.body(), Map.class);
            boolean ok = resp.statusCode() >= 200 && resp.statusCode() < 300;
            String msg = String.valueOf(map.getOrDefault(ok ? "message" : "error",
                    ok ? "OK" : "HTTP " + resp.statusCode()));
            String tok = map.get("token") == null ? null : String.valueOf(map.get("token"));
            String nick = map.get("nickname") == null ? null : String.valueOf(map.get("nickname"));
            return new Result(ok, msg, tok, nick);
        } catch (Exception e) {
            return Result.fail("无法连接服务器：" + e.getClass().getSimpleName());
        }
    }
}
