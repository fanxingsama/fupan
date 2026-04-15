package com.meirifupan.backend.model;

import java.util.List;

/**
 * 市场情绪指标。
 */
public record MarketIndicators(
        Double sealRate,
        Double limitRatio,
        int limitUpTotal,
        int limitDownTotal,
        int brokenCount,
        Double yesterdayLimitPremium,
        Double yesterdayLimitWinRate,
        Double yesterdayBrokenAvg,
        int maxBoardHeight,
        List<LadderLevel> boardLadder,
        String emotionPhase,
        String emotionLabel,
        String emotionColor,
        String emotionDescription,
        double speculationScore,
        double continuationScore,
        double breadthScore,
        List<RiskSignal> riskSignals
) {

    public record LadderLevel(int height, int count) {}

    public record RiskSignal(String level, String text) {}
}
