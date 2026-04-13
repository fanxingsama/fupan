package com.meirifupan.backend.model;

public record MarketStats(
        int upCount,
        int downCount,
        int flatCount,
        int firstLimitCount
) {
}
