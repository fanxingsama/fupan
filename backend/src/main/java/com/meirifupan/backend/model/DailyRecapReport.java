package com.meirifupan.backend.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 每日复盘报告。
 */
public record DailyRecapReport(
        LocalDate tradeDate,
        OffsetDateTime createdAt,
        MarketStats marketStats,
        List<BoardIndexSnapshot> boardIndexes,
        List<StockRecord> brokenLimitToday,
        List<StockRecord> brokenLimitYesterdayFeedback,
        List<StockRecord> limitUpToday,
        List<StockRecord> limitUpYesterdayFeedback,
        List<StockRecord> firstLimitToday,
        List<StockRecord> firstLimitYesterdayFeedback,
        List<StockRecord> limitDownToday,
        List<SectorRecord> topUpSectors,
        List<SectorRecord> topDownSectors,
        List<StockRecord> top10DayGainGemStar,
        List<StockRecord> top10DayGainMainBoard,
        Map<String, Integer> firstLimitSectorFocus,
        String dataSource,
        String notes
) {
}
