package com.meirifupan.backend.model;

import java.util.List;

/**
 * 复盘详情响应体 —— 将原始报告、计算指标、趋势数据打包返回给前端。
 * <p>
 * 前端只需要 GET 一次请求就能拿到所有展示所需的数据，无需做任何计算。
 */
public record RecapDetailResponse(
        /** 原始复盘数据 */
        DailyRecapReport report,
        /** 后端计算的市场指标 */
        MarketIndicators indicators,
        /** 最近 N 个交易日的趋势数据点 */
        List<TrendPoint> trendPoints
) {
}
