package com.meirifupan.backend.model;

import java.time.OffsetDateTime;
import java.util.List;

public record AiInsight(
        boolean enabled,
        boolean cached,
        String provider,
        String model,
        String status,
        String marketConclusion,
        String marketStyle,
        List<String> keySignals,
        List<ThemeInsight> themes,
        List<LeaderInsight> leaders,
        List<String> actionPlan,
        List<String> riskAlerts,
        String disclaimer,
        OffsetDateTime generatedAt
) {

    public record ThemeInsight(
            String name,
            String strength,
            String driver,
            String observation
    ) {}

    public record LeaderInsight(
            String code,
            String name,
            String role,
            String reason,
            String signal,
            String risk
    ) {}
}
