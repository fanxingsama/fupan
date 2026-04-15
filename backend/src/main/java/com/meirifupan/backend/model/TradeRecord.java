package com.meirifupan.backend.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TradeRecord(
        String id,
        LocalDate tradeDate,
        String code,
        String name,
        String side,
        double price,
        int quantity,
        double amount,
        double fee,
        String sourceFile,
        OffsetDateTime importedAt
) {
}
