package com.meirifupan.backend.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 每日复盘报告 —— 整个系统的核心数据模型。
 * <p>
 * 一次完整的复盘采集会产生一份 DailyRecapReport，包含：
 * <ul>
 *   <li>大盘概况统计（上涨/下跌/平盘/首板数量）</li>
 *   <li>当日炸板票及昨日炸板反馈</li>
 *   <li>当日连板票及昨日连板反馈（2 板及以上）</li>
 *   <li>当日首板票 &amp; 跌停票</li>
 *   <li>板块涨跌幅排行（上涨 / 下跌各取前列）</li>
 *   <li>创业板/科创板 &amp; 主板的 10 日涨幅前列</li>
 *   <li>首板集中板块分布（用于前端聚焦柱状图）</li>
 * </ul>
 * 该 record 会被 Jackson 序列化为 JSON 存入 data/ 目录，也会作为 REST 接口的响应体返回给前端。
 */
public record DailyRecapReport(
        // 本次复盘对应的交易日。
        LocalDate tradeDate,
        // 报告生成时间。
        OffsetDateTime createdAt,
        // 市场整体统计数据。
        MarketStats marketStats,
        // 当日炸板票。
        List<StockRecord> brokenLimitToday,
        // 昨日炸板票在今日的反馈。
        List<StockRecord> brokenLimitYesterdayFeedback,
        // 当日连板票，仅保留 2 板及以上。
        List<StockRecord> limitUpToday,
        // 昨日连板票在今日的反馈。
        List<StockRecord> limitUpYesterdayFeedback,
        // 当日首板票。
        List<StockRecord> firstLimitToday,
        // 当日跌停票。
        List<StockRecord> limitDownToday,
        // 上涨板块前列。
        List<SectorRecord> topUpSectors,
        // 下跌板块前列。
        List<SectorRecord> topDownSectors,
        // 创业板 / 科创板 10 日涨幅前列。
        List<StockRecord> top10DayGainGemStar,
        // 主板 10 日涨幅前列。
        List<StockRecord> top10DayGainMainBoard,
        // 首板集中板块，用于前端聚焦图。
        Map<String, Integer> firstLimitSectorFocus,
        // 当前报告使用的数据源。
        String dataSource,
        // 对本次采集口径的补充说明。
        String notes
) {
}
