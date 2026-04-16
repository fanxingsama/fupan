package com.meirifupan.backend.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AiChatRequest(
        List<AiChatMessage> history,
        @NotBlank(message = "message 不能为空")
        String message
) {
}
