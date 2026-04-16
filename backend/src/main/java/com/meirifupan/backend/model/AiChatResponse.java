package com.meirifupan.backend.model;

import java.time.OffsetDateTime;

public record AiChatResponse(
        boolean enabled,
        String provider,
        String model,
        String status,
        String answer,
        String error,
        OffsetDateTime repliedAt
) {
}
