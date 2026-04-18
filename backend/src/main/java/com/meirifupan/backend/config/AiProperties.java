package com.meirifupan.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        boolean enabled,
        String provider,
        String apiKey,
        String baseUrl,
        String model,
        String summaryStorageRoot,
        int timeoutSeconds
) {
}
