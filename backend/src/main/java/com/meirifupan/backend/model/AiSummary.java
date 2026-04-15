package com.meirifupan.backend.model;

import java.time.OffsetDateTime;
import java.util.List;

public record AiSummary(
        boolean enabled,
        boolean cached,
        String provider,
        String model,
        String status,
        String summary,
        List<String> bullets,
        String disclaimer,
        OffsetDateTime generatedAt
) {
}
