package com.meirifupan.backend.model;

import java.time.OffsetDateTime;
import java.util.List;

public record ThemeTrackingDetail(
        String themeName,
        String tradeDate,
        List<String> themeCatalysts,
        String themeStatus,
        double themeScore,
        String verdict,
        List<String> evidenceList,
        List<String> counterEvidence,
        List<ThemeTrackingStock> coreStocks,
        List<ThemeTrackingStock> highBoardStocks,
        List<ThemeTrackingStock> midLevelFollowers,
        List<ThemeTrackingStock> lowLevelAttempts,
        List<String> riskSignals,
        List<String> nextDayCheckpoints,
        List<ThemeTrackingHistoryItem> history,
        double confidence,
        boolean aiEnabled,
        boolean aiGenerated,
        String provider,
        String model,
        String disclaimer,
        OffsetDateTime generatedAt
) {
    public ThemeTrackingSummary toSummary() {
        int highBoardCount = highBoardStocks.size();
        int limitUpCount = highBoardCount + coreStocks.stream().mapToInt(stock -> parseBoardHeight(stock.boardHeight()) >= 1 ? 1 : 0).sum();
        return new ThemeTrackingSummary(
                themeName,
                tradeDate,
                themeStatus,
                themeScore,
                verdict,
                themeCatalysts,
                limitUpCount,
                highBoardCount,
                maxBoardHeight(),
                coreStocks.stream().map(ThemeTrackingStock::name).limit(3).toList(),
                nextDayCheckpoints,
                confidence
        );
    }

    private int maxBoardHeight() {
        int max = 0;
        for (ThemeTrackingStock stock : coreStocks) {
            max = Math.max(max, parseBoardHeight(stock.boardHeight()));
        }
        for (ThemeTrackingStock stock : highBoardStocks) {
            max = Math.max(max, parseBoardHeight(stock.boardHeight()));
        }
        return max;
    }

    private int parseBoardHeight(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
