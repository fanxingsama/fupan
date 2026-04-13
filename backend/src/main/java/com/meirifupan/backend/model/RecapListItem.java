package com.meirifupan.backend.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record RecapListItem(
        LocalDate tradeDate,
        OffsetDateTime createdAt,
        String dataSource,
        int upCount,
        int downCount,
        int firstLimitCount
) {
}
