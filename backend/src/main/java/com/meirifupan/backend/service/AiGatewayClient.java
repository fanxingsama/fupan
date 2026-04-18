package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meirifupan.backend.config.AiProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class AiGatewayClient {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiGatewayClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, properties.timeoutSeconds())))
                .build();
    }

    public boolean isConfigured() {
        return properties.enabled()
                && !isBlank(properties.apiKey())
                && !isBlank(properties.baseUrl())
                && !isBlank(properties.model());
    }

    public ObjectNode newChatRequest(JsonNode messages) {
        return objectMapper.createObjectNode()
                .put("model", properties.model())
                .set("messages", messages);
    }

    public String chatCompletion(JsonNode requestBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(AiEndpointResolver.resolveChatCompletionsUrl(properties.baseUrl())))
                .timeout(Duration.ofSeconds(Math.max(10, properties.timeoutSeconds())))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " " + response.body());
        }

        return extractContent(objectMapper.readTree(response.body())).trim();
    }

    private String extractContent(JsonNode root) {
        if (root.path("choices").isArray() && !root.path("choices").isEmpty()) {
            JsonNode choice = root.path("choices").get(0);
            JsonNode content = choice.path("message").path("content");
            if (content.isTextual() && !content.asText().isBlank()) {
                return content.asText();
            }
            if (content.isArray()) {
                StringBuilder builder = new StringBuilder();
                for (JsonNode item : content) {
                    if (item.has("text") && !item.path("text").asText().isBlank()) {
                        if (!builder.isEmpty()) {
                            builder.append("\n");
                        }
                        builder.append(item.path("text").asText());
                    }
                }
                if (!builder.toString().isBlank()) {
                    return builder.toString();
                }
            }
            JsonNode text = choice.path("text");
            if (text.isTextual() && !text.asText().isBlank()) {
                return text.asText();
            }
        }

        if (!root.path("reply").asText().isBlank()) {
            return root.path("reply").asText();
        }
        if (!root.path("output_text").asText().isBlank()) {
            return root.path("output_text").asText();
        }
        throw new IllegalStateException("无法从 AI 响应中提取文本");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
