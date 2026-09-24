package com.cavedream.core.net;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 云存档 API 客户端：整包存档 JSON 与后端 t_save_slot 同步（无限槽，按字符串槽名 keying）。
 * 本地=主存、云端=cache：写为 write-through（upload），读为 read-through（list/download）。
 * 任何失败静默返回空/false，绝不抛异常阻塞游戏主流程。
 */
public class CloudSaveClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public CloudSaveClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /** 上传一个槽位（write-through）。slotKey = 本地存档名。返回 null=成功，否则为失败原因。 */
    public String upload(String token, String slotKey, String saveName, long seed,
                         String gameVersion, String worldStateJson) {
        if (token == null || token.isBlank()) {
            return "未登录（无 token）";
        }
        try {
            Map<String, Object> body = Map.of(
                    "saveName", saveName == null ? slotKey : saveName,
                    "seed", seed,
                    "gameVersion", gameVersion == null ? "" : gameVersion,
                    "worldStateJson", worldStateJson);
            HttpResponse<String> resp = send("PUT", "/api/saves/" + enc(slotKey), token,
                    json.writeValueAsString(body));
            if (resp == null) {
                return "无响应（连不上后端？）";
            }
            if (!ok(resp)) {
                return "HTTP " + resp.statusCode() + " " + resp.body();
            }
            return null;
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /** 列出云端槽位摘要（slot_key / save_name / seed / game_version / updated_at）。失败返回空表。 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> list(String token) {
        if (token == null || token.isBlank()) {
            return List.of();
        }
        try {
            HttpResponse<String> resp = send("GET", "/api/saves", token, null);
            if (resp == null || !ok(resp)) {
                return List.of();
            }
            Map<String, Object> map = json.readValue(resp.body(), Map.class);
            Object slots = map.get("slots");
            return slots instanceof List ? (List<Map<String, Object>>) slots : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 下载某槽整包 JSON（read-through）；失败返回 null。 */
    @SuppressWarnings("unchecked")
    public String download(String token, String slotKey) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            HttpResponse<String> resp = send("GET", "/api/saves/" + enc(slotKey), token, null);
            if (resp == null || !ok(resp)) {
                return null;
            }
            Map<String, Object> map = json.readValue(resp.body(), Map.class);
            Object state = map.get("world_state_json");
            return state == null ? null : String.valueOf(state);
        } catch (Exception e) {
            return null;
        }
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.BodyPublisher pub = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("X-Token", token)
                .method(method, pub);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean ok(HttpResponse<String> r) {
        return r.statusCode() >= 200 && r.statusCode() < 300;
    }
}
