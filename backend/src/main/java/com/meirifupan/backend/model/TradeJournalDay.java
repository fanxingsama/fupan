package com.meirifupan.backend.model;

import java.time.LocalDate;
import java.util.List;

public record TradeJournalDay(
        LocalDate tradeDate,
        int tradeCount,
        int buyCount,
        int sellCount,
        double totalAmount,
        MarketContext marketContext,
        List<TradeRecord> trades
) {
    public record MarketContext(
            String emotionLabel,
            String tradeMode,
            String marketBias,
            String headline,
            String leadingTheme
    ) {
    }
}
