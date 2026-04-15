package com.meirifupan.backend.model;

import java.util.List;

/**
 * 次日交易计划。
 */
public record TradePlan(
        String headline,
        String marketBias,
        String tradeMode,
        String positionAdvice,
        String executionSummary,
        List<String> nextDayFocus,
        List<String> riskFocus,
        List<ThemeScore> primaryThemes,
        List<CandidatePool> candidatePools,
        List<WatchStock> watchStocks,
        List<PlanStep> schedule
) {

    public record ThemeScore(
            String name,
            double score,
            String phase,
            String comment
    ) {}

    public record CandidatePool(
            String key,
            String title,
            String description,
            List<WatchStock> stocks
    ) {}

    public record WatchStock(
            String code,
            String name,
            String role,
            String theme,
            double score,
            String summary,
            String planA,
            String planB,
            String riskNote
    ) {}

    public record PlanStep(
            String window,
            String title,
            String focus
    ) {}
}
