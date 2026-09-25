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
 * 极简 OpenAI 兼容 chat 客户端（智谱 / 硅基流动 / Groq 等通用，仅 base url + model + key 不同）。
 * 同步调用，须由上层放到后台线程；任何失败返回 null（由上层走离线兜底）。绝不抛异常阻塞游戏。
 */
public final class LlmClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4)).build();

    private LlmClient() {
    }

    /** 一次对话补全：system 人设 + user 输入 → 助手回复文本；失败返回 null。 */
    @SuppressWarnings("unchecked")
    public static String chat(String baseUrl, String apiKey, String model, String system, String user) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)),
                    "temperature", 0.9,
                    "max_tokens", 220);
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return null;
            }
            Map<String, Object> map = JSON.readValue(resp.body(), Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) map.get("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            Object content = msg == null ? null : msg.get("content");
            return content == null ? null : String.valueOf(content).trim();
        } catch (Exception e) {
            return null;
        }
    }
}
