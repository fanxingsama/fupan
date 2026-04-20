package com.meirifupan.backend.model;

public record ThemeTrackingHistoryItem(
        String tradeDate,
        String themeStatus,
        double themeScore,
        int maxBoardHeight,
        String leadStock,
        String summary,
        String validationStatus
) {
}
