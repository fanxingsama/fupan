package com.meirifupan.backend.model;

/**
 * 趋势数据点。
 */
public record TrendPoint(
        String tradeDate,
        int upCount,
        int maxBoardHeight,
        int firstLimitCount,
        double sealRate,
        int limitUpTotal,
        int brokenCount,
        double yesterdayLimitPremium,
        double limitRatio,
        double totalTurnover
) {
}
