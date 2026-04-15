package com.meirifupan.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trade-journal")
public record TradeJournalProperties(
        String storageRoot
) {
}
