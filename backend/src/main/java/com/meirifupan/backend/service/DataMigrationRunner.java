package com.meirifupan.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meirifupan.backend.config.AiProperties;
import com.meirifupan.backend.config.RecapProperties;
import com.meirifupan.backend.config.TradeJournalProperties;
import com.meirifupan.backend.model.AiSummary;
import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.TradeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 启动时自动将旧 JSON 文件数据迁移到 SQLite。
 * 只在数据库表为空时执行迁移，避免重复导入。
 */
@Component
public class DataMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationRunner.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RecapProperties recapProperties;
    private final TradeJournalProperties tradeJournalProperties;
    private final AiProperties aiProperties;

    public DataMigrationRunner(JdbcTemplate jdbc, ObjectMapper objectMapper,
                               RecapProperties recapProperties,
                               TradeJournalProperties tradeJournalProperties,
                               AiProperties aiProperties) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.recapProperties = recapProperties;
        this.tradeJournalProperties = tradeJournalProperties;
        this.aiProperties = aiProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateRecaps();
        migrateTradeJournal();
        migrateAiSummaries();
    }

    private void migrateRecaps() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM recap_report", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        Path dir = Path.of(recapProperties.storageRoot()).toAbsolutePath();
        if (!Files.isDirectory(dir)) {
            return;
        }

        int migrated = 0;
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> jsonFiles = stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
            for (Path path : jsonFiles) {
                try (InputStream is = Files.newInputStream(path)) {
                    DailyRecapReport report = objectMapper.readValue(is, DailyRecapReport.class);
                    String json = objectMapper.writeValueAsString(report);
                    String createdAt = report.createdAt() != null ? report.createdAt().toString() : null;
                    jdbc.update(
                            "INSERT OR IGNORE INTO recap_report (trade_date, report_json, created_at) VALUES (?, ?, ?)",
                            report.tradeDate().toString(), json, createdAt
                    );
                    migrated++;
                } catch (Exception e) {
                    log.warn("迁移复盘文件失败: {}", path, e);
                }
            }
        } catch (Exception e) {
            log.warn("扫描复盘目录失败: {}", dir, e);
        }

        if (migrated > 0) {
            log.info("已将 {} 个复盘 JSON 文件迁移到 SQLite", migrated);
        }
    }

    private void migrateTradeJournal() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM trade_record", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        String root = tradeJournalProperties.storageRoot();
        Path dir = Path.of(root == null || root.isBlank() ? "trade-journal" : root).toAbsolutePath();
        if (!Files.isDirectory(dir)) {
            return;
        }

        int migrated = 0;
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> jsonFiles = stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
            for (Path path : jsonFiles) {
                try {
                    List<TradeRecord> records = objectMapper.readValue(
                            Files.readString(path), new TypeReference<List<TradeRecord>>() {});
                    for (TradeRecord record : records) {
                        jdbc.update(
                                "INSERT OR IGNORE INTO trade_record (id, trade_date, code, name, side, price, quantity, amount, fee, source_file, imported_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                record.id(),
                                record.tradeDate().toString(),
                                record.code(),
                                record.name(),
                                record.side(),
                                record.price(),
                                record.quantity(),
                                record.amount(),
                                record.fee(),
                                record.sourceFile(),
                                record.importedAt() != null ? record.importedAt().toString() : null
                        );
                        migrated++;
                    }
                } catch (Exception e) {
                    log.warn("迁移交易日志文件失败: {}", path, e);
                }
            }
        } catch (Exception e) {
            log.warn("扫描交易日志目录失败: {}", dir, e);
        }

        if (migrated > 0) {
            log.info("已将 {} 条交易记录迁移到 SQLite", migrated);
        }
    }

    private void migrateAiSummaries() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ai_summary", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        String root = aiProperties.summaryStorageRoot();
        Path dir = Path.of(root == null || root.isBlank() ? "ai-summaries" : root).toAbsolutePath();
        if (!Files.isDirectory(dir)) {
            return;
        }

        int migrated = 0;
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> jsonFiles = stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
            for (Path path : jsonFiles) {
                try (InputStream is = Files.newInputStream(path)) {
                    AiSummary summary = objectMapper.readValue(is, AiSummary.class);
                    String json = objectMapper.writeValueAsString(summary);
                    String tradeDate = path.getFileName().toString().replace(".json", "");
                    jdbc.update(
                            "INSERT OR IGNORE INTO ai_summary (trade_date, summary_json) VALUES (?, ?)",
                            tradeDate, json
                    );
                    migrated++;
                } catch (Exception e) {
                    log.warn("迁移AI摘要文件失败: {}", path, e);
                }
            }
        } catch (Exception e) {
            log.warn("扫描AI摘要目录失败: {}", dir, e);
        }

        if (migrated > 0) {
            log.info("已将 {} 个AI摘要迁移到 SQLite", migrated);
        }
    }
}
