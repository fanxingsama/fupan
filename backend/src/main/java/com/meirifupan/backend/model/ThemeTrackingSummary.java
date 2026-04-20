package com.meirifupan.backend.model;

import java.util.List;

public record ThemeTrackingSummary(
        String themeName,
        String tradeDate,
        String themeStatus,
        double themeScore,
        String summary,
        List<String> themeCatalysts,
        int limitUpCount,
        int highBoardCount,
        int maxBoardHeight,
        List<String> coreStockNames,
        List<String> nextDayCheckpoints,
        double confidence
) {
}
