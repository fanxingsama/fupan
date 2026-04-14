package com.meirifupan.backend.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 复盘列表条目 —— 用于历史记录面板和日历面板的轻量展示。
 * <p>
 * 只携带交易日期、生成时间和几个关键统计数字，
 * 避免一次性把所有历史 DailyRecapReport 全量加载到前端，
 * 前端通过点击列表条目再按需拉取完整报告。
 */
public record RecapListItem(
        // 交易日。
        LocalDate tradeDate,
        // 该日复盘文件最后一次生成时间。
        OffsetDateTime createdAt,
        // 数据来源。
        String dataSource,
        // 上涨家数。
        int upCount,
        // 下跌家数。
        int downCount,
        // 首板数量。
        int firstLimitCount
) {
}
