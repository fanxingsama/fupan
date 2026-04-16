package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meirifupan.backend.config.AiProperties;
import com.meirifupan.backend.model.AiChatMessage;
import com.meirifupan.backend.model.AiChatRequest;
import com.meirifupan.backend.model.AiChatResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AiChatService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiChatService(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public AiChatResponse chat(AiChatRequest request) {
        if (!aiEnabled()) {
            return new AiChatResponse(
                    false,
                    aiProperties.provider(),
                    aiProperties.model(),
                    "disabled",
                    "",
                    "AI 未启用，请先配置 AI_ENABLED、AI_API_KEY、AI_BASE_URL、AI_MODEL。",
                    OffsetDateTime.now()
            );
        }

        try {
            String answer = requestAnswer(request);
            return new AiChatResponse(
                    true,
                    aiProperties.provider(),
                    aiProperties.model(),
                    "ready",
                    answer,
                    "",
                    OffsetDateTime.now()
            );
        } catch (Exception ex) {
            return new AiChatResponse(
                    true,
                    aiProperties.provider(),
                    aiProperties.model(),
                    "error",
                    "",
                    ex.getMessage(),
                    OffsetDateTime.now()
            );
        }
    }

    private String requestAnswer(AiChatRequest chatRequest) throws IOException, InterruptedException {
        JsonNode requestBody = objectMapper.createObjectNode()
                .put("model", aiProperties.model())
                .set("messages", buildMessages(chatRequest));

        HttpRequest request = HttpRequest.newBuilder(URI.create(AiEndpointResolver.resolveChatCompletionsUrl(aiProperties.baseUrl())))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + aiProperties.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return extractContent(root).trim();
    }

    private JsonNode buildMessages(AiChatRequest chatRequest) {
        var messages = objectMapper.createArrayNode();

        List<AiChatMessage> history = chatRequest.history() == null ? List.of() : chatRequest.history();
        for (AiChatMessage item : history) {
            if (item == null || isBlank(item.content())) {
                continue;
            }
            messages.add(objectMapper.createObjectNode()
                    .put("role", normalizeRole(item.role()))
                    .put("content", item.content().trim()));
        }

        messages.add(objectMapper.createObjectNode()
                .put("role", "user")
                .put("content", chatRequest.message().trim()));
        return messages;
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

    private String normalizeRole(String role) {
        return "assistant".equalsIgnoreCase(role) ? "assistant" : "user";
    }

    private boolean aiEnabled() {
        return aiProperties.enabled() && !isBlank(aiProperties.apiKey()) && !isBlank(aiProperties.baseUrl());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
