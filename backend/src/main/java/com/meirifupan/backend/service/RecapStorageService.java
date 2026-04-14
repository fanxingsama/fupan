package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meirifupan.backend.config.RecapProperties;
import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.RecapListItem;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 复盘存储服务 —— 负责复盘报告的本地文件持久化与读取。
 * <p>
 * 本系统不使用传统数据库（如 MySQL、PostgreSQL），而是采用
 * “每个交易日一个 JSON 文件”的策略存储在本地 data/ 目录下。
 * 文件格式：{tradeDate}.json，例如 data/2026-04-14.json。
 * <p>
 * 这样做的好处：
 * <ul>
 *   <li>零依赖：不需要安装和配置任何数据库</li>
 *   <li>可读性强：用文本编辑器就能直接查看 / 编辑历史复盘数据</li>
 *   <li>备份简单：复制 data/ 目录即可完成备份</li>
 * </ul>
 *
 * @see RecapCaptureService 采集服务会调用本类的 save() 方法来落盘
 */
@Service
public class RecapStorageService {

    private final Path storageRoot;
    private final ObjectMapper objectMapper;

    public RecapStorageService(RecapProperties properties, ObjectMapper objectMapper) throws IOException {
        this.storageRoot = Path.of(properties.storageRoot()).toAbsolutePath();
        this.objectMapper = objectMapper;
        Files.createDirectories(this.storageRoot);
    }

    /**
     * 每个交易日保存为一个 json 文件，方便后续直接回看历史复盘。
     */
    public DailyRecapReport save(DailyRecapReport report) {
        Path path = filePath(report.tradeDate());
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputStream, report);
            return report;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save recap report: " + path, e);
        }
    }

    public Optional<DailyRecapReport> findByDate(LocalDate tradeDate) {
        Path path = filePath(tradeDate);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            return Optional.of(objectMapper.readValue(inputStream, DailyRecapReport.class));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read recap report: " + path, e);
        }
    }

    /**
     * 扫描 data 目录下已有的复盘文件，用于历史记录和日历面板展示。
     */
    public List<RecapListItem> list() {
        List<RecapListItem> items = new ArrayList<>();
        try (Stream<Path> stream = Files.list(storageRoot)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try (InputStream inputStream = Files.newInputStream(path)) {
                            DailyRecapReport report = objectMapper.readValue(inputStream, DailyRecapReport.class);
                            items.add(new RecapListItem(
                                    report.tradeDate(),
                                    report.createdAt(),
                                    report.dataSource(),
                                    report.marketStats().upCount(),
                                    report.marketStats().downCount(),
                                    report.marketStats().firstLimitCount()
                            ));
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to read recap list item: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan recap storage: " + storageRoot, e);
        }
        items.sort(Comparator.comparing(RecapListItem::tradeDate).reversed());
        return items;
    }

    private Path filePath(LocalDate tradeDate) {
        return storageRoot.resolve(tradeDate + ".json");
    }
}
