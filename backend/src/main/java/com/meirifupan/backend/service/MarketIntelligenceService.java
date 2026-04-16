package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meirifupan.backend.config.RecapProperties;
import com.meirifupan.backend.model.MarketIntelligence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class MarketIntelligenceService {
    // AI-READABLE-CHAIN:
    // Raw intelligence aggregation entry.
    // Executes the Python multi-source collector and caches the result in SQLite.

    private final RecapProperties properties;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public MarketIntelligenceService(RecapProperties properties, ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    public MarketIntelligence loadOrCollect(LocalDate tradeDate, boolean refresh) {
        if (!refresh) {
            List<MarketIntelligence> cached = jdbc.query(
                    "SELECT payload_json FROM market_intelligence WHERE trade_date = ?",
                    (rs, rowNum) -> {
                        try {
                            return objectMapper.readValue(rs.getString("payload_json"), MarketIntelligence.class);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to deserialize market intelligence", e);
                        }
                    },
                    tradeDate.toString()
            );
            if (!cached.isEmpty()) {
                return cached.get(0);
            }
        }

        MarketIntelligence intelligence = collect(tradeDate);
        save(tradeDate, intelligence);
        return intelligence;
    }

    private void save(LocalDate tradeDate, MarketIntelligence intelligence) {
        try {
            String json = objectMapper.writeValueAsString(intelligence);
            jdbc.update(
                    "INSERT INTO market_intelligence (trade_date, payload_json) VALUES (?, ?) " +
                            "ON CONFLICT(trade_date) DO UPDATE SET payload_json = excluded.payload_json",
                    tradeDate.toString(),
                    json
            );
        } catch (Exception ignored) {
        }
    }

    private MarketIntelligence collect(LocalDate tradeDate) {
        Path scriptPath = Path.of(properties.intelligenceScript()).toAbsolutePath();
        if (!Files.exists(scriptPath)) {
            throw new IllegalStateException("Intelligence script not found: " + scriptPath);
        }

        List<String> command = new ArrayList<>();
        command.add(properties.pythonExecutable());
        command.add("-X");
        command.add("utf8");
        command.add(scriptPath.toString());
        command.add("--date");
        command.add(tradeDate.toString());
        command.add("--sleep");
        command.add(String.valueOf(properties.sleepSeconds()));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(scriptPath.getParent().toFile());
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        processBuilder.environment().put("PYTHONUTF8", "1");
        processBuilder.environment().remove("HTTP_PROXY");
        processBuilder.environment().remove("HTTPS_PROXY");
        processBuilder.environment().remove("ALL_PROXY");
        processBuilder.environment().remove("http_proxy");
        processBuilder.environment().remove("https_proxy");
        processBuilder.environment().remove("all_proxy");
        processBuilder.environment().put("NO_PROXY", "*");
        processBuilder.environment().put("no_proxy", "*");

        try {
            Process process = processBuilder.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Intelligence collector failed: " + stderr);
            }
            return objectMapper.readValue(stdout, MarketIntelligence.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to execute intelligence collector.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Intelligence collector interrupted.", e);
        }
    }
}
