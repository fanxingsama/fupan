package com.meirifupan.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        RecapProperties.class,
        AiProperties.class,
        TradeJournalProperties.class
})
public class RecapConfig {
}
