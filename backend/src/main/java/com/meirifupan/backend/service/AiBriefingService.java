package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meirifupan.backend.config.AiProperties;
import com.meirifupan.backend.model.AiBriefing;
import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.MarketIndicators;
import com.meirifupan.backend.model.MarketIntelligence;
import com.meirifupan.backend.model.SectorRecord;
import com.meirifupan.backend.model.TradePlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class AiBriefingService {
    // AI-READABLE-LLM-LAYER:
    // Intelligence briefing layer.
    // Raw intelligence is collected first, then the LLM turns it into a structured briefing.

    private static final String DEFAULT_DISCLAIMER = "AI 情报仅用于信息整理与盘后研究，不构成投资建议，请结合盘面独立判断。";
    private static final String DISABLED_DISCLAIMER = "AI 未启用。请在环境变量中配置 AI_ENABLED=true、AI_API_KEY、AI_BASE_URL、AI_MODEL 后再使用。";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final AiGatewayClient aiGatewayClient;
    private final JdbcTemplate jdbc;
    private final MarketIntelligenceService marketIntelligenceService;

    public AiBriefingService(AiProperties properties,
                             ObjectMapper objectMapper,
                             JdbcTemplate jdbc,
                             MarketIntelligenceService marketIntelligenceService,
                             AiGatewayClient aiGatewayClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.marketIntelligenceService = marketIntelligenceService;
        this.aiGatewayClient = aiGatewayClient;
    }

    public AiBriefing generateOrLoad(DailyRecapReport report,
                                     List<DailyRecapReport> recentReports,
                                     MarketIndicators indicators,
                                     TradePlan tradePlan,
                                     boolean refresh) {
        if (!aiGatewayClient.isConfigured()) {
            return new AiBriefing(
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
                    DISABLED_DISCLAIMER,
                    null
            );
        }

        String tradeDate = report.tradeDate().toString();
        if (!refresh) {
            List<AiBriefing> cached = jdbc.query(
                    "SELECT briefing_json FROM ai_briefing WHERE trade_date = ?",
                    (rs, rowNum) -> {
                        try {
                            AiBriefing briefing = objectMapper.readValue(rs.getString("briefing_json"), AiBriefing.class);
                            return new AiBriefing(
                                    true,
                                    true,
                                    briefing.provider(),
                                    briefing.model(),
                                    briefing.status(),
                                    briefing.headline(),
                                    briefing.briefing(),
                                    safeList(briefing.themePulses()),
                                    safeList(briefing.stockFocuses()),
                                    safeList(briefing.timeline()),
                                    safeList(briefing.tomorrowSignals()),
                                    briefing.disclaimer(),
                                    briefing.generatedAt()
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
            MarketIntelligence intelligence = marketIntelligenceService.loadOrCollect(report.tradeDate(), refresh);
            AiBriefing briefing = requestBriefing(report, recentReports, indicators, tradePlan, intelligence);
            saveBriefing(tradeDate, briefing);
            return briefing;
        } catch (Exception ex) {
            return new AiBriefing(
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
                    "AI 情报生成失败: " + ex.getMessage(),
                    OffsetDateTime.now()
            );
        }
    }

    private void saveBriefing(String tradeDate, AiBriefing briefing) {
        try {
            String json = objectMapper.writeValueAsString(briefing);
            jdbc.update(
                    "INSERT INTO ai_briefing (trade_date, briefing_json) VALUES (?, ?) " +
                            "ON CONFLICT(trade_date) DO UPDATE SET briefing_json = excluded.briefing_json",
                    tradeDate, json
            );
        } catch (Exception ignored) {
        }
    }

    private AiBriefing requestBriefing(DailyRecapReport report,
                                       List<DailyRecapReport> recentReports,
                                       MarketIndicators indicators,
                                       TradePlan tradePlan,
                                       MarketIntelligence intelligence) throws IOException, InterruptedException {
        String prompt = buildPrompt(report, recentReports, indicators, tradePlan, intelligence);

        JsonNode requestBody = aiGatewayClient.newChatRequest(objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "system")
                                .put("content", """
                                        你是A股短线情报助手。你的任务是基于实时盘面热榜、题材热词、个股新闻和当日复盘数据，整理一份交易情报。
                                        不要编造不存在的新闻，不要承诺收益，不要直接预测具体涨跌。
                                        必须只输出 JSON 对象，不要输出 markdown，不要加解释。
                                        """))
                        .add(objectMapper.createObjectNode()
                                .put("role", "user")
                                .put("content", prompt)));
        String content = aiGatewayClient.chatCompletion(requestBody);
        return parseBriefing(content, report, recentReports, tradePlan, intelligence);
    }

    private String buildPrompt(DailyRecapReport report,
                               List<DailyRecapReport> recentReports,
                               MarketIndicators indicators,
                               TradePlan tradePlan,
                               MarketIntelligence intelligence) throws IOException {
        String currentSummary = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "tradeDate", report.tradeDate(),
                "marketStats", report.marketStats(),
                "topUpSectors", safeList(report.topUpSectors()).stream().limit(6).toList(),
                "firstLimitSectorFocus", report.firstLimitSectorFocus(),
                "limitUpToday", safeList(report.limitUpToday()).stream().limit(10).toList(),
                "firstLimitToday", safeList(report.firstLimitToday()).stream().limit(10).toList(),
                "brokenLimitToday", safeList(report.brokenLimitToday()).stream().limit(10).toList()
        ));

        List<Map<String, Object>> recentContext = safeList(recentReports).stream()
                .sorted(Comparator.comparing(DailyRecapReport::tradeDate))
                .skip(Math.max(0, recentReports.size() - 5))
                .map(item -> Map.of(
                        "tradeDate", item.tradeDate().toString(),
                        "upCount", item.marketStats() != null ? item.marketStats().upCount() : 0,
                        "downCount", item.marketStats() != null ? item.marketStats().downCount() : 0,
                        "firstLimitCount", item.marketStats() != null ? item.marketStats().firstLimitCount() : 0,
                        "topSectors", safeList(item.topUpSectors()).stream().limit(4).map(SectorRecord::name).toList()
                ))
                .toList();

        String intelligenceJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(intelligence);
        String currentJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(currentSummary);
        String recentJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(recentContext);
        String indicatorsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(indicators);
        String tradePlanJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tradePlan);

        return """
                请输出一份“AI 情报中心” JSON，面向短线复盘与次日准备。

                JSON schema:
                {
                  "headline": "一句话情报标题",
                  "briefing": "100字内的情报简报",
                  "themePulses": [
                    {"name": "题材", "trend": "升温/分歧/轮动/退潮", "reason": "原因", "nextSignal": "次日观察点"}
                  ],
                  "stockFocuses": [
                    {"code": "代码", "name": "名称", "tag": "前排/卡位/修复/辨识度", "reason": "为何值得看", "catalyst": "次日看什么"}
                  ],
                  "timeline": [
                    {"tradeDate": "日期", "summary": "情报变化"}
                  ],
                  "tomorrowSignals": ["3-5条次日情报信号"]
                }

                要求:
                1. 优先基于实时情报数据输出，不依赖长历史。
                2. 可以用当前复盘数据和近几日摘要补充盘面背景，但不要虚构外部消息。
                3. themePulses 最多4条，stockFocuses 最多5条，timeline 最多5条。
                4. 强调主流题材、热榜聚焦、个股新闻催化和次日观察点。

                实时情报:
                %s

                当前交易日数据:
                %s

                近几日摘要:
                %s

                当前指标:
                %s

                平台已有次日计划:
                %s
                """.formatted(intelligenceJson, currentJson, recentJson, indicatorsJson, tradePlanJson);
    }

    private AiBriefing parseBriefing(String content,
                                     DailyRecapReport report,
                                     List<DailyRecapReport> recentReports,
                                     TradePlan tradePlan,
                                     MarketIntelligence intelligence) throws IOException {
        JsonNode root = objectMapper.readTree(AiJsonSupport.extractJsonObject(content));

        List<AiBriefing.ThemePulse> themePulses = new ArrayList<>();
        if (root.path("themePulses").isArray()) {
            for (JsonNode item : root.path("themePulses")) {
                themePulses.add(new AiBriefing.ThemePulse(
                        AiJsonSupport.text(item, "name"),
                        AiJsonSupport.text(item, "trend"),
                        AiJsonSupport.text(item, "reason"),
                        AiJsonSupport.text(item, "nextSignal")
                ));
            }
        }

        List<AiBriefing.StockFocus> stockFocuses = new ArrayList<>();
        if (root.path("stockFocuses").isArray()) {
            for (JsonNode item : root.path("stockFocuses")) {
                stockFocuses.add(new AiBriefing.StockFocus(
                        AiJsonSupport.text(item, "code"),
                        AiJsonSupport.text(item, "name"),
                        AiJsonSupport.text(item, "tag"),
                        AiJsonSupport.text(item, "reason"),
                        AiJsonSupport.text(item, "catalyst")
                ));
            }
        }

        List<AiBriefing.BriefingNote> timeline = new ArrayList<>();
        if (root.path("timeline").isArray()) {
            for (JsonNode item : root.path("timeline")) {
                timeline.add(new AiBriefing.BriefingNote(
                        AiJsonSupport.text(item, "tradeDate"),
                        AiJsonSupport.text(item, "summary")
                ));
            }
        }

        if (themePulses.isEmpty()) {
            for (MarketIntelligence.TopicPulse topic : safeList(intelligence.topicPulses()).stream().limit(4).toList()) {
                themePulses.add(new AiBriefing.ThemePulse(
                        topic.name(),
                        "升温",
                        "热词热度 " + topic.heat() + "，样本股 " + topic.sampleStock(),
                        "观察相关热股是否继续位于榜单前列。"
                ));
            }
        }

        if (stockFocuses.isEmpty()) {
            for (MarketIntelligence.HotStock stock : safeList(intelligence.hotStocks()).stream().limit(5).toList()) {
                stockFocuses.add(new AiBriefing.StockFocus(
                        stock.code(),
                        stock.name(),
                        "热榜前排",
                        "当前热榜排名靠前，涨跌幅 " + stock.changePercent(),
                        "关注热词 " + String.join(" / ", safeList(stock.keywords()))
                ));
            }
            if (stockFocuses.isEmpty()) {
                for (TradePlan.WatchStock stock : safeList(tradePlan.watchStocks()).stream().limit(4).toList()) {
                    stockFocuses.add(new AiBriefing.StockFocus(
                            stock.code(),
                            stock.name(),
                            stock.role(),
                            stock.summary(),
                            stock.planA()
                    ));
                }
            }
        }

        if (timeline.isEmpty()) {
            timeline = buildTimeline(recentReports, intelligence);
        }

        return new AiBriefing(
                true,
                false,
                properties.provider(),
                properties.model(),
                "ready",
                firstNonBlank(AiJsonSupport.text(root, "headline"), tradePlan.headline()),
                firstNonBlank(AiJsonSupport.text(root, "briefing"), "基于实时热榜、热词和当日盘面生成的情报简报。"),
                safeList(themePulses).stream().filter(item -> !isBlank(item.name())).limit(4).toList(),
                safeList(stockFocuses).stream().filter(item -> !isBlank(item.name())).limit(5).toList(),
                safeList(timeline).stream().filter(item -> !isBlank(item.tradeDate())).limit(5).toList(),
                limitStrings(strings(root.path("tomorrowSignals")), 5),
                DEFAULT_DISCLAIMER,
                OffsetDateTime.now()
        );
    }

    private List<AiBriefing.BriefingNote> buildTimeline(List<DailyRecapReport> recentReports, MarketIntelligence intelligence) {
        List<AiBriefing.BriefingNote> notes = new ArrayList<>(safeList(recentReports).stream()
                .sorted(Comparator.comparing(DailyRecapReport::tradeDate))
                .skip(Math.max(0, recentReports.size() - 4))
                .map(report -> new AiBriefing.BriefingNote(report.tradeDate().toString(), timelineSummary(report)))
                .toList());

        if (intelligence != null) {
            String topics = safeList(intelligence.topicPulses()).stream()
                    .limit(3)
                    .map(MarketIntelligence.TopicPulse::name)
                    .reduce((a, b) -> a + " / " + b)
                    .orElse("热榜主题待确认");
            notes.add(new AiBriefing.BriefingNote(intelligence.tradeDate(), "实时热榜聚焦于 " + topics + "。"));
        }
        return notes.stream().limit(5).toList();
    }

    private String timelineSummary(DailyRecapReport report) {
        String topSector = safeList(report.topUpSectors()).stream().findFirst().map(SectorRecord::name).orElse("主线待确认");
        int limitCount = safeList(report.limitUpToday()).size() + safeList(report.firstLimitToday()).size();
        int brokenCount = safeList(report.brokenLimitToday()).size();
        return topSector + "活跃，涨停" + limitCount + "家，炸板" + brokenCount + "家。";
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
        return safeList(items).stream().map(String::trim).filter(item -> !item.isBlank()).limit(maxSize).toList();
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
