package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meirifupan.backend.config.AiProperties;
import com.meirifupan.backend.model.AiInsight;
import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.MarketIndicators;
import com.meirifupan.backend.model.TradePlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiInsightService {
    // AI-READABLE-LLM-LAYER:
    // Structured analysis layer.
    // Input is recap + indicators + trade plan, output is JSON-like trading analysis.

    private static final String DEFAULT_DISCLAIMER = "AI 输出仅作为复盘与研究辅助，不构成投资建议，请结合盘面自行决策。";
    private static final String DISABLED_DISCLAIMER = "AI 未启用。请在环境变量中配置 AI_ENABLED=true、AI_API_KEY、AI_BASE_URL、AI_MODEL 后再使用。";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final JdbcTemplate jdbc;

    public AiInsightService(AiProperties properties, ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.jdbc = jdbc;
    }

    public AiInsight generateOrLoad(DailyRecapReport report, MarketIndicators indicators, TradePlan tradePlan, boolean refresh) {
        if (!properties.enabled() || isBlank(properties.apiKey()) || isBlank(properties.baseUrl())) {
            return new AiInsight(
                    false,
                    false,
                    properties.provider(),
                    properties.model(),
                    "disabled",
                    "",
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    DISABLED_DISCLAIMER,
                    null
            );
        }

        String tradeDate = report.tradeDate().toString();
        if (!refresh) {
            List<AiInsight> cached = jdbc.query(
                    "SELECT insight_json FROM ai_insight WHERE trade_date = ?",
                    (rs, rowNum) -> {
                        try {
                            AiInsight insight = objectMapper.readValue(rs.getString("insight_json"), AiInsight.class);
                            return new AiInsight(
                                    true,
                                    true,
                                    insight.provider(),
                                    insight.model(),
                                    insight.status(),
                                    insight.marketConclusion(),
                                    insight.marketStyle(),
                                    safeList(insight.keySignals()),
                                    safeList(insight.themes()),
                                    safeList(insight.leaders()),
                                    safeList(insight.actionPlan()),
                                    safeList(insight.riskAlerts()),
                                    insight.disclaimer(),
                                    insight.generatedAt()
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
            AiInsight insight = requestInsight(report, indicators, tradePlan);
            saveInsight(tradeDate, insight);
            return insight;
        } catch (Exception ex) {
            return new AiInsight(
                    true,
                    false,
                    properties.provider(),
                    properties.model(),
                    "error",
                    "",
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "AI 分析生成失败: " + ex.getMessage(),
                    OffsetDateTime.now()
            );
        }
    }

    private void saveInsight(String tradeDate, AiInsight insight) {
        try {
            String json = objectMapper.writeValueAsString(insight);
            jdbc.update(
                    "INSERT INTO ai_insight (trade_date, insight_json) VALUES (?, ?) " +
                            "ON CONFLICT(trade_date) DO UPDATE SET insight_json = excluded.insight_json",
                    tradeDate, json
            );
        } catch (Exception ignored) {
        }
    }

    private AiInsight requestInsight(DailyRecapReport report, MarketIndicators indicators, TradePlan tradePlan)
            throws IOException, InterruptedException {
        String prompt = buildPrompt(report, indicators, tradePlan);

        JsonNode requestBody = objectMapper.createObjectNode()
                .put("model", properties.model())
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "system")
                                .put("content", """
                                        你是A股短线交易分析助手。你的任务是基于结构化复盘数据，输出克制、专业、可执行的交易分析。
                                        不要承诺收益，不要编造事实。
                                        必须只输出 JSON 对象，不要输出 markdown 代码块，不要补充解释。
                                        """))
                        .add(objectMapper.createObjectNode()
                                .put("role", "user")
                                .put("content", prompt)));

        HttpRequest request = HttpRequest.newBuilder(URI.create(AiEndpointResolver.resolveChatCompletionsUrl(properties.baseUrl())))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " " + response.body());
        }

        String content = extractContent(objectMapper.readTree(response.body()));
        return parseInsight(content, report, tradePlan);
    }

    private String buildPrompt(DailyRecapReport report, MarketIndicators indicators, TradePlan tradePlan) throws IOException {
        String reportJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        String indicatorJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(indicators);
        String planJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tradePlan);

        return """
                请根据以下 A 股短线复盘数据，输出一份结构化分析 JSON。

                JSON schema:
                {
                  "marketConclusion": "不超过80字，概括市场状态",
                  "marketStyle": "一句话概括当前盘面风格",
                  "keySignals": ["3-5条关键信号"],
                  "themes": [
                    {"name": "题材名", "strength": "主升/发酵/轮动/分歧", "driver": "驱动原因", "observation": "次日观察点"}
                  ],
                  "leaders": [
                    {"code": "股票代码", "name": "股票名", "role": "龙头/前排/修复/辨识度标", "reason": "入选原因", "signal": "次日关注信号", "risk": "主要风险"}
                  ],
                  "actionPlan": ["3-4条次日动作建议"],
                  "riskAlerts": ["2-4条主要风险提示"]
                }

                输出要求:
                1. 只使用输入里已经出现的信息，不要编造消息面。
                2. themes 最多 3 个，leaders 最多 5 个。
                3. actionPlan 要具体到看什么、等什么、放弃什么。
                4. riskAlerts 要贴近短线交易，而不是泛泛而谈。

                原始复盘:
                %s

                指标:
                %s

                平台已有次日计划:
                %s
                """.formatted(reportJson, indicatorJson, planJson);
    }

    private AiInsight parseInsight(String content, DailyRecapReport report, TradePlan tradePlan) throws IOException {
        JsonNode root = objectMapper.readTree(extractJson(content));

        List<AiInsight.ThemeInsight> themes = new ArrayList<>();
        if (root.path("themes").isArray()) {
            for (JsonNode item : root.path("themes")) {
                themes.add(new AiInsight.ThemeInsight(
                        text(item, "name"),
                        text(item, "strength"),
                        text(item, "driver"),
                        text(item, "observation")
                ));
            }
        }

        List<AiInsight.LeaderInsight> leaders = new ArrayList<>();
        if (root.path("leaders").isArray()) {
            for (JsonNode item : root.path("leaders")) {
                leaders.add(new AiInsight.LeaderInsight(
                        text(item, "code"),
                        text(item, "name"),
                        text(item, "role"),
                        text(item, "reason"),
                        text(item, "signal"),
                        text(item, "risk")
                ));
            }
        }

        if (leaders.isEmpty()) {
            for (TradePlan.WatchStock stock : safeList(tradePlan.watchStocks()).stream().limit(3).toList()) {
                leaders.add(new AiInsight.LeaderInsight(
                        stock.code(),
                        stock.name(),
                        stock.role(),
                        stock.summary(),
                        stock.planA(),
                        stock.riskNote()
                ));
            }
        }

        if (themes.isEmpty()) {
            for (TradePlan.ThemeScore theme : safeList(tradePlan.primaryThemes()).stream().limit(3).toList()) {
                themes.add(new AiInsight.ThemeInsight(
                        theme.name(),
                        theme.phase(),
                        theme.comment(),
                        "观察题材是否继续获得竞价与前排助攻。"
                ));
            }
        }

        return new AiInsight(
                true,
                false,
                properties.provider(),
                properties.model(),
                "ready",
                firstNonBlank(text(root, "marketConclusion"), report.notes(), tradePlan.headline()),
                firstNonBlank(text(root, "marketStyle"), tradePlan.tradeMode()),
                limitStrings(strings(root.path("keySignals")), 5),
                limitThemes(themes, 3),
                limitLeaders(leaders, 5),
                limitStrings(strings(root.path("actionPlan")), 4),
                limitStrings(strings(root.path("riskAlerts")), 4),
                DEFAULT_DISCLAIMER,
                OffsetDateTime.now()
        );
    }

    private String extractContent(JsonNode root) {
        if (root.path("choices").isArray() && !root.path("choices").isEmpty()) {
            JsonNode choice = root.path("choices").get(0);
            JsonNode content = choice.path("message").path("content");
            if (!content.isMissingNode() && !content.asText().isBlank()) {
                return content.asText();
            }
            JsonNode text = choice.path("text");
            if (!text.isMissingNode() && !text.asText().isBlank()) {
                return text.asText();
            }
        }
        if (!root.path("reply").asText().isBlank()) {
            return root.path("reply").asText();
        }
        if (!root.path("output_text").asText().isBlank()) {
            return root.path("output_text").asText();
        }
        throw new IllegalStateException("无法从响应中提取 AI 文本");
    }

    private String extractJson(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("AI 未返回有效 JSON");
        }
        return text.substring(start, end + 1);
    }

    private List<String> strings(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (!node.isArray()) {
            return result;
        }
        for (JsonNode item : node) {
            String text = item.asText("").trim();
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private List<String> limitStrings(List<String> items, int maxSize) {
        return safeList(items).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .limit(maxSize)
                .toList();
    }

    private List<AiInsight.ThemeInsight> limitThemes(List<AiInsight.ThemeInsight> items, int maxSize) {
        return safeList(items).stream()
                .filter(item -> !isBlank(item.name()))
                .limit(maxSize)
                .toList();
    }

    private List<AiInsight.LeaderInsight> limitLeaders(List<AiInsight.LeaderInsight> items, int maxSize) {
        return safeList(items).stream()
                .filter(item -> !isBlank(item.name()))
                .limit(maxSize)
                .toList();
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }
}
