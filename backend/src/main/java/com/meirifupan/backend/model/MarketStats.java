package com.meirifupan.backend.model;

/**
 * 全市场涨跌概况统计 —— 作为 {@link DailyRecapReport} 的一个嵌套字段。
 * <p>
 * 记录当天收盘时全市场的上涨、下跌、平盘家数以及首板数量，
 * 前端用于"收盘看板"四个摘要卡片的展示。
 */
public record MarketStats(
        // 全市场上涨家数。
        int upCount,
        // 全市场下跌家数。
        int downCount,
        // 全市场平盘家数。
        int flatCount,
        // 首板数量。
        int firstLimitCount
) {
}
