package com.meirifupan.backend.model;

/**
 * 全市场涨跌概况统计。
 */
public record MarketStats(
        int upCount,
        int downCount,
        int flatCount,
        int firstLimitCount,
        String totalTurnover
) {
}
