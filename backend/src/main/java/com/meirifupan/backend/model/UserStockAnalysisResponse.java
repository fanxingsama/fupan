package com.meirifupan.backend.model;

import java.time.OffsetDateTime;
import java.util.List;

public record UserStockAnalysisResponse(
        boolean enabled,
        String provider,
        String model,
        String status,
        int uploadedImageCount,
        int analyzedDayCount,
        String overallConclusion,
        String tradingStyleProfile,
        List<String> styleTags,
        List<String> recurringPatterns,
        List<String> methodology,
        List<String> riskWarnings,
        List<DayAnalysis> dayAnalyses,
        String disclaimer,
        OffsetDateTime generatedAt
) {

    public record DayAnalysis(
            String tradeDate,
            String imageName,
            String imageType,
            String summary,
            List<String> holdings,
            List<String> inferredReasons,
            List<String> probableBuyPoints,
            List<String> probableSellPoints,
            List<String> volumePriceClues,
            List<String> newsDrivers,
            List<String> nextDayFocus
    ) {}
}
