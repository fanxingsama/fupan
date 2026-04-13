package com.meirifupan.backend.service;

import com.meirifupan.backend.config.RecapProperties;
import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.provider.MarketRecapProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class RecapCaptureService {

    private final RecapProperties properties;
    private final RecapStorageService storageService;
    private final List<MarketRecapProvider> providers;

    public RecapCaptureService(
            RecapProperties properties,
            RecapStorageService storageService,
            List<MarketRecapProvider> providers
    ) {
        this.properties = properties;
        this.storageService = storageService;
        this.providers = providers;
    }

    public DailyRecapReport capture(LocalDate tradeDate) {
        MarketRecapProvider provider = providers.stream()
                .filter(item -> item.name().equalsIgnoreCase(properties.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到 provider: " + properties.provider()));
        DailyRecapReport report = provider.capture(tradeDate);
        return storageService.save(report);
    }
}
