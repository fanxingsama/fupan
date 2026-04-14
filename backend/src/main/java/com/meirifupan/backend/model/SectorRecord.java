package com.meirifupan.backend.model;

/**
 * 板块涨跌记录 —— 表示一个行业/概念板块的当日涨跌幅。
 * <p>
 * 用于 {@link DailyRecapReport#topUpSectors()} 和 {@link DailyRecapReport#topDownSectors()}，
 * 前端会分别在"上涨板块前列"和"下跌板块前列"两个横向柱状图中渲染。
 */
public record SectorRecord(
        // 板块名称。
        String name,
        // 板块涨跌幅。
        String changePercent,
        // 进入榜单的补充说明，例如领涨股或 fallback 推导说明。
        String reason
) {
}
