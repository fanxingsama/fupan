package com.meirifupan.backend.model;

import java.time.OffsetDateTime;
import java.util.List;

public record AiBriefing(
        boolean enabled,
        boolean cached,
        String provider,
        String model,
        String status,
        String headline,
        String briefing,
        List<ThemePulse> themePulses,
        List<StockFocus> stockFocuses,
        List<BriefingNote> timeline,
        List<String> tomorrowSignals,
        String disclaimer,
        OffsetDateTime generatedAt
) {

    public record ThemePulse(
            String name,
            String trend,
            String reason,
            String nextSignal
    ) {}

    public record StockFocus(
            String code,
            String name,
            String tag,
            String reason,
            String catalyst
    ) {}

    public record BriefingNote(
            String tradeDate,
            String summary
    ) {}
}
