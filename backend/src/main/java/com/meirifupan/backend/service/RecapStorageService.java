package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.RecapListItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 复盘存储服务 —— 负责复盘报告的 SQLite 持久化与读取。
 */
@Service
public class RecapStorageService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RecapStorageService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public DailyRecapReport save(DailyRecapReport report) {
        try {
            String json = objectMapper.writeValueAsString(report);
            String createdAt = report.createdAt() != null ? report.createdAt().toString() : null;
            jdbc.update(
                    "INSERT INTO recap_report (trade_date, report_json, created_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT(trade_date) DO UPDATE SET report_json = excluded.report_json, created_at = excluded.created_at",
                    report.tradeDate().toString(), json, createdAt
            );
            return report;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save recap report: " + report.tradeDate(), e);
        }
    }

    public Optional<DailyRecapReport> findByDate(LocalDate tradeDate) {
        List<DailyRecapReport> results = jdbc.query(
                "SELECT report_json FROM recap_report WHERE trade_date = ?",
                (rs, rowNum) -> {
                    try {
                        return objectMapper.readValue(rs.getString("report_json"), DailyRecapReport.class);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to deserialize recap report", e);
                    }
                },
                tradeDate.toString()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<RecapListItem> list() {
        return jdbc.query(
                "SELECT report_json FROM recap_report ORDER BY trade_date DESC",
                (rs, rowNum) -> {
                    try {
                        DailyRecapReport report = objectMapper.readValue(rs.getString("report_json"), DailyRecapReport.class);
                        return new RecapListItem(
                                report.tradeDate(),
                                report.createdAt(),
                                report.dataSource(),
                                report.marketStats().upCount(),
                                report.marketStats().downCount(),
                                report.marketStats().firstLimitCount()
                        );
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to deserialize recap list item", e);
                    }
                }
        );
    }

    public List<DailyRecapReport> loadRecent(LocalDate targetDate, int count) {
        return jdbc.query(
                "SELECT report_json FROM recap_report WHERE trade_date <= ? ORDER BY trade_date DESC LIMIT ?",
                (rs, rowNum) -> {
                    try {
                        return objectMapper.readValue(rs.getString("report_json"), DailyRecapReport.class);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to deserialize recap report", e);
                    }
                },
                targetDate.toString(), count
        ).stream()
         .sorted(Comparator.comparing(DailyRecapReport::tradeDate))
         .toList();
    }
}
