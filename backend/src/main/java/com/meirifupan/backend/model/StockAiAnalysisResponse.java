package com.meirifupan.backend.model;

import java.time.OffsetDateTime;
import java.util.List;

public record StockAiAnalysisResponse(
        boolean enabled,
        String provider,
        String model,
        String status,
        String stockCode,
        String stockName,
        String timeframe,
        String timeframeLabel,
        String source,
        int analyzedBars,
        String windowStart,
        String windowEnd,
        Double latestPrice,
        Double periodChangePercent,
        Double rangeHigh,
        Double rangeLow,
        Double averageVolume,
        Double recentVolumeRatio,
        String headline,
        String summary,
        String trendBias,
        String actionBias,
        String confidence,
        List<String> volumePriceSignals,
        List<String> buyPoints,
        List<String> sellPoints,
        List<String> learningPoints,
        List<String> riskWarnings,
        List<CandleBar> candles,
        String disclaimer,
        OffsetDateTime generatedAt
) {
    public record CandleBar(
            String time,
            double open,
            double close,
            double high,
            double low,
            double volume,
            double amount,
            Double changePercent,
            Double amplitudePercent
    ) {
    }
}
