package com.meirifupan.backend.model;

import java.util.List;

/**
 * 复盘详情响应体。
 */
public record RecapDetailResponse(
        DailyRecapReport report,
        MarketIndicators indicators,
        TradePlan tradePlan,
        List<TrendPoint> trendPoints
) {
}
