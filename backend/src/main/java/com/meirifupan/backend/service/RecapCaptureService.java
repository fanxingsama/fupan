package com.meirifupan.backend.service;

import com.meirifupan.backend.config.RecapProperties;
import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.provider.MarketRecapProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 复盘采集服务 —— 协调“采集数据”和“落盘存储”两个步骤。
 * <p>
 * 工作流程：
 * <ol>
 *   <li>根据 application.yml 中配置的 recap.provider 找到对应的 {@link MarketRecapProvider} 实现</li>
 *   <li>调用 provider.capture() 从上游数据源采集原始数据并生成 {@link DailyRecapReport}</li>
 *   <li>通过 {@link RecapStorageService#save} 把报告序列化为 JSON 写入 data/ 目录</li>
 * </ol>
 * 前端点击"触发采集"按钮时，Controller 会调用本类的 capture() 方法。
 */
@Service
public class RecapCaptureService {
    // AI-READABLE-CHAIN:
    // Java-to-Python bridge for recap collection.
    // Provider selection happens here, actual upstream collection happens in the selected provider.

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

    /**
     * 按当前配置的数据源执行采集，并把结果持久化到本地。
     */
    public DailyRecapReport capture(LocalDate tradeDate) {
        MarketRecapProvider provider = providers.stream()
                .filter(item -> item.name().equalsIgnoreCase(properties.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到 provider: " + properties.provider()));
        DailyRecapReport report = provider.capture(tradeDate);
        return storageService.save(report);
    }
}
