package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meirifupan.backend.config.AiProperties;
import com.meirifupan.backend.model.AiSummary;
import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.MarketIndicators;
import com.meirifupan.backend.model.TradePlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiSummaryService {
    // AI-READABLE-LLM-LAYER:
    // Human-readable recap summary layer.
    // Input is recap + indicators + trade plan, output is free-form summary text.

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final AiGatewayClient aiGatewayClient;
    private final JdbcTemplate jdbc;

    public AiSummaryService(AiProperties properties, ObjectMapper objectMapper, JdbcTemplate jdbc, AiGatewayClient aiGatewayClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.aiGatewayClient = aiGatewayClient;
    }

    public AiSummary generateOrLoad(DailyRecapReport report, MarketIndicators indicators, TradePlan tradePlan, boolean refresh) {
        if (!aiGatewayClient.isConfigured()) {
            return new AiSummary(
                    false,
                    false,
                    properties.provider(),
                    properties.model(),
                    "disabled",
                    "",
                    List.of(),
                    "AI 未启用。请在后端配置 ai.enabled=true、AI_API_KEY、AI_BASE_URL、AI_MODEL 后再使用。",
                    null
            );
        }

        String tradeDate = report.tradeDate().toString();
        if (!refresh) {
            List<AiSummary> cached = jdbc.query(
                    "SELECT summary_json FROM ai_summary WHERE trade_date = ?",
                    (rs, rowNum) -> {
                        try {
                            AiSummary s = objectMapper.readValue(rs.getString("summary_json"), AiSummary.class);
                            return new AiSummary(
                                    true, true,
                                    s.provider(), s.model(), s.status(),
                                    s.summary(), s.bullets(), s.disclaimer(), s.generatedAt()
                            );
                        } catch (Exception e) {
                            return null;
                        }
                    },
                    tradeDate
            );
            if (!cached.isEmpty() && cached.get(0) != null) {
                return cached.get(0);
            }
        }

        try {
            AiSummary summary = requestSummary(report, indicators, tradePlan);
            saveSummary(tradeDate, summary);
            return summary;
        } catch (Exception ex) {
            return new AiSummary(
                    true,
                    false,
                    properties.provider(),
                    properties.model(),
                    "error",
                    "",
                    List.of(),
                    "AI 摘要生成失败：" + ex.getMessage(),
                    OffsetDateTime.now()
            );
        }
    }

    private void saveSummary(String tradeDate, AiSummary summary) {
        try {
            String json = objectMapper.writeValueAsString(summary);
            jdbc.update(
                    "INSERT INTO ai_summary (trade_date, summary_json) VALUES (?, ?) " +
                    "ON CONFLICT(trade_date) DO UPDATE SET summary_json = excluded.summary_json",
                    tradeDate, json
            );
        } catch (Exception ignored) {
        }
    }

    private AiSummary requestSummary(DailyRecapReport report, MarketIndicators indicators, TradePlan tradePlan) throws IOException, InterruptedException {
        String prompt = buildPrompt(report, indicators, tradePlan);

        JsonNode requestBody = aiGatewayClient.newChatRequest(objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "system")
                                .put("content", "你是A股短线交易复盘助手。请根据输入数据输出高密度、克制、可执行的复盘总结。不要保证收益，不要空话。")
                        )
                        .add(objectMapper.createObjectNode()
                                .put("role", "user")
                                .put("content", prompt)
                        )
                );
        String content = aiGatewayClient.chatCompletion(requestBody);
        List<String> bullets = parseBullets(content);

        return new AiSummary(
                true,
                false,
                properties.provider(),
                properties.model(),
                "ready",
                content,
                bullets,
                "AI 输出仅作为复盘辅助，不构成交易建议，仍需你结合盘面自行决策。",
                OffsetDateTime.now()
        );
    }

    private String buildPrompt(DailyRecapReport report, MarketIndicators indicators, TradePlan tradePlan) throws IOException {
        String reportJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        String indicatorJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(indicators);
        String planJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tradePlan);

        return """
                请根据以下 A 股短线复盘数据，输出中文复盘总结。

                输出格式要求：
                1. 先给 1 段不超过 120 字的"市场结论"。
                2. 再给 4 条要点，每条单独一行，以"- "开头。
                3. 要点尽量围绕：情绪阶段、主线强弱、接力环境、次日策略。
                4. 不要编造没有给出的事实，不要保证涨跌，不要出现"建议满仓"这种表达。

                原始复盘：
                %s

                指标：
                %s

                平台生成的次日计划：
                %s
                """.formatted(reportJson, indicatorJson, planJson);
    }

    private List<String> parseBullets(String content) {
        List<String> bullets = new ArrayList<>();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ")) {
                bullets.add(trimmed.substring(2).trim());
            }
        }
        return bullets;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
