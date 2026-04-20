package com.meirifupan.backend.model;

public record ThemeTrackingStock(
        String code,
        String name,
        String role,
        String behaviorTag,
        String boardHeight,
        String changePercent,
        String amount,
        String reason,
        String observation
) {
}
