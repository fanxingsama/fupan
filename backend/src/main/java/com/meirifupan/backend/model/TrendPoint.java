package com.meirifupan.backend.model;

/**
 * 趋势数据点 —— 用于前端图表展示的单日指标摘要。
 * <p>
 * 后端遍历最近 N 个交易日的 DailyRecapReport，为每一天生成一条 TrendPoint，
 * 前端直接取数组绘制折线图，无需任何计算。
 */
public record TrendPoint(
        /** 交易日，格式 "2026-04-14" */
        String tradeDate,
        /** 上涨家数 */
        int upCount,
        /** 连板最高高度 */
        int maxBoardHeight,
        /** 首板数量 */
        int firstLimitCount,
        /** 封板率(%) */
        double sealRate,
        /** 涨停总数 */
        int limitUpTotal,
        /** 炸板数 */
        int brokenCount,
        /** 昨涨停溢价率(%) */
        double yesterdayLimitPremium,
        /** 涨跌停比 */
        double limitRatio
) {
}
