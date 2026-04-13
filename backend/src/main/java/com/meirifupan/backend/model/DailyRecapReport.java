package com.meirifupan.backend.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record DailyRecapReport(
        LocalDate tradeDate,
        OffsetDateTime createdAt,
        MarketStats marketStats,
        List<StockRecord> brokenLimitToday,
        List<StockRecord> brokenLimitYesterdayFeedback,
        List<StockRecord> limitUpToday,
        List<StockRecord> limitUpYesterdayFeedback,
        List<StockRecord> firstLimitToday,
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
