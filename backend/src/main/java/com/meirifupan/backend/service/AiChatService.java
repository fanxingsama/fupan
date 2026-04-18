package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meirifupan.backend.config.AiProperties;
import com.meirifupan.backend.model.AiChatMessage;
import com.meirifupan.backend.model.AiChatRequest;
import com.meirifupan.backend.model.AiChatResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AiChatService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final AiGatewayClient aiGatewayClient;

    public AiChatService(AiProperties aiProperties, ObjectMapper objectMapper, AiGatewayClient aiGatewayClient) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.aiGatewayClient = aiGatewayClient;
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
        JsonNode requestBody = aiGatewayClient.newChatRequest(buildMessages(chatRequest));
        return aiGatewayClient.chatCompletion(requestBody);
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

    private String normalizeRole(String role) {
        return "assistant".equalsIgnoreCase(role) ? "assistant" : "user";
    }

    private boolean aiEnabled() {
        return aiGatewayClient.isConfigured();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
