package com.meirifupan.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recap")
public record RecapProperties(
        String storageRoot,
        String provider,
        String pythonExecutable,
        String collectorScript,
        String intelligenceScript,
        double sleepSeconds
) {
}
