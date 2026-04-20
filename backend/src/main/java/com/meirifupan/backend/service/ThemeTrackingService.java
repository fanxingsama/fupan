package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.meirifupan.backend.config.AiProperties;
import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.MarketIndicators;
import com.meirifupan.backend.model.MarketIntelligence;
import com.meirifupan.backend.model.SectorRecord;
import com.meirifupan.backend.model.StockRecord;
import com.meirifupan.backend.model.ThemeTrackingDetail;
import com.meirifupan.backend.model.ThemeTrackingHistoryItem;
import com.meirifupan.backend.model.ThemeTrackingStock;
import com.meirifupan.backend.model.ThemeTrackingSummary;
import com.meirifupan.backend.model.TradePlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ThemeTrackingService {

    private static final String DEFAULT_DISCLAIMER = "AI output is for post-market research and evidence organization only.";
    private static final String DISABLED_DISCLAIMER = "AI is disabled. Theme tracking falls back to rule-based evidence only.";
    private static final int MAX_THEMES = 5;

    private final RecapStorageService recapStorageService;
    private final MarketIntelligenceService marketIntelligenceService;
    private final IndicatorService indicatorService;
    private final TradePlanService tradePlanService;
    private final AiGatewayClient aiGatewayClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public ThemeTrackingService(RecapStorageService recapStorageService,
                                MarketIntelligenceService marketIntelligenceService,
                                IndicatorService indicatorService,
                                TradePlanService tradePlanService,
                                AiGatewayClient aiGatewayClient,
                                AiProperties aiProperties,
                                ObjectMapper objectMapper,
                                JdbcTemplate jdbc) {
        this.recapStorageService = recapStorageService;
        this.marketIntelligenceService = marketIntelligenceService;
        this.indicatorService = indicatorService;
        this.tradePlanService = tradePlanService;
        this.aiGatewayClient = aiGatewayClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    public List<ThemeTrackingSummary> list(LocalDate tradeDate, boolean refresh) {
        return ensureThemeDetails(tradeDate, refresh).stream()
                .map(ThemeTrackingDetail::toSummary)
                .toList();
    }

    public ThemeTrackingDetail detail(LocalDate tradeDate, String themeName, boolean refresh) {
        return ensureThemeDetails(tradeDate, refresh).stream()
                .filter(detail -> detail.themeName().equals(themeName))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Theme not found: " + themeName));
    }

    public List<ThemeTrackingHistoryItem> history(LocalDate tradeDate, String themeName, int days) {
        DailyRecapReport report = loadReport(tradeDate);
        List<DailyRecapReport> reports = recapStorageService.loadRecent(tradeDate, Math.max(3, Math.min(days, 10)));
        return buildHistory(themeName, reports, report).stream()
                .limit(Math.max(1, Math.min(days, 10)))
                .toList();
    }

    private List<ThemeTrackingDetail> ensureThemeDetails(LocalDate tradeDate, boolean refresh) {
        if (!refresh) {
            List<ThemeTrackingDetail> cached = loadCached(tradeDate);
            if (!cached.isEmpty()) {
                return cached;
            }
        }

        DailyRecapReport report = loadReport(tradeDate);
        List<DailyRecapReport> reports = recapStorageService.loadRecent(tradeDate, 10);
        List<ThemeTrackingDetail> generated = generate(report, reports, refresh);
        save(tradeDate, generated);
        return generated;
    }

    private DailyRecapReport loadReport(LocalDate tradeDate) {
        return recapStorageService.findByDate(tradeDate)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Recap not found for " + tradeDate));
    }

    private List<ThemeTrackingDetail> loadCached(LocalDate tradeDate) {
        return jdbc.query(
                "SELECT payload_json FROM theme_tracking WHERE trade_date = ? ORDER BY theme_name",
                (rs, rowNum) -> {
                    try {
                        return objectMapper.readValue(rs.getString("payload_json"), ThemeTrackingDetail.class);
                    } catch (IOException ex) {
                        throw new IllegalStateException("Failed to deserialize theme tracking detail", ex);
                    }
                },
                tradeDate.toString()
        ).stream().sorted(Comparator.comparingDouble(ThemeTrackingDetail::themeScore).reversed()).toList();
    }

    private void save(LocalDate tradeDate, List<ThemeTrackingDetail> details) {
        jdbc.update("DELETE FROM theme_tracking WHERE trade_date = ?", tradeDate.toString());
        for (ThemeTrackingDetail detail : details) {
            try {
                jdbc.update(
                        "INSERT INTO theme_tracking (trade_date, theme_name, payload_json, created_at) VALUES (?, ?, ?, ?)",
                        tradeDate.toString(),
                        detail.themeName(),
                        objectMapper.writeValueAsString(detail),
                        OffsetDateTime.now().toString()
                );
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to save theme tracking detail", ex);
            }
        }
    }

    private List<ThemeTrackingDetail> generate(DailyRecapReport report,
                                               List<DailyRecapReport> recentReports,
                                               boolean refresh) {
        MarketIntelligence intelligence = marketIntelligenceService.loadOrCollect(report.tradeDate(), refresh);
        MarketIndicators indicators = indicatorService.calculate(report, recentReports);
        TradePlan tradePlan = tradePlanService.buildPlan(report, indicators);

        List<ThemeSnapshot> snapshots = collectCandidateThemes(report, intelligence, tradePlan).stream()
                .map(theme -> buildRuleSnapshot(theme, report, intelligence, tradePlan))
                .filter(snapshot -> snapshot.themeScore() > 8)
                .sorted(Comparator.comparingDouble(ThemeSnapshot::themeScore).reversed())
                .limit(MAX_THEMES)
                .toList();

        List<ThemeTrackingDetail> details = new ArrayList<>();
        for (ThemeSnapshot snapshot : snapshots) {
            List<ThemeTrackingHistoryItem> history = buildHistory(snapshot.themeName(), recentReports, report);
            ThemeTrackingDetail detail = toDetail(snapshot, history);
            details.add(maybeEnrichWithAi(detail, report, indicators, intelligence));
        }
        return details;
    }

    private List<String> collectCandidateThemes(DailyRecapReport report,
                                                MarketIntelligence intelligence,
                                                TradePlan tradePlan) {
        LinkedHashSet<String> themes = new LinkedHashSet<>();
        safeList(tradePlan.primaryThemes()).forEach(theme -> themes.add(cleanThemeName(theme.name())));
        safeMap(report.firstLimitSectorFocus()).keySet().forEach(theme -> themes.add(cleanThemeName(theme)));
        safeList(report.topUpSectors()).forEach(sector -> themes.add(cleanThemeName(sector.name())));
        safeList(intelligence.themeClusters()).forEach(cluster -> themes.add(cleanThemeName(cluster.name())));
        safeList(intelligence.topicPulses()).forEach(pulse -> themes.add(cleanThemeName(pulse.name())));
        return themes.stream().filter(theme -> !theme.isBlank()).toList();
    }

    private ThemeSnapshot buildRuleSnapshot(String themeName,
                                            DailyRecapReport report,
                                            MarketIntelligence intelligence,
                                            TradePlan tradePlan) {
        ThemeMatchContext matchContext = buildMatchContext(themeName, intelligence);
        List<StockRecord> allStocks = new ArrayList<>();
        allStocks.addAll(safeList(report.limitUpToday()));
        allStocks.addAll(safeList(report.firstLimitToday()));
        allStocks.addAll(safeList(report.brokenLimitToday()));
        allStocks.addAll(safeList(report.top10DayGainMainBoard()));
        allStocks.addAll(safeList(report.top10DayGainGemStar()));

        List<StockRecord> matched = dedupeStocks(allStocks).stream()
                .filter(stock -> matchesTheme(stock, matchContext))
                .toList();

        List<StockRecord> sortedByStrength = matched.stream()
                .sorted(Comparator.comparingDouble((StockRecord stock) -> stockStrength(stock, matchContext.relatedStockNames())).reversed())
                .toList();

        List<StockRecord> coreStocks = sortedByStrength.stream().limit(4).toList();
        List<StockRecord> highBoardStocks = sortedByStrength.stream()
                .filter(stock -> parseBoardHeight(stock.boardHeight()) >= 2)
                .limit(4)
                .toList();
        List<StockRecord> midFollowers = sortedByStrength.stream()
                .filter(stock -> parseBoardHeight(stock.boardHeight()) == 1 || parseBoardHeight(stock.boardHeight()) == 2)
                .skip(Math.min(2, sortedByStrength.size()))
                .limit(4)
                .toList();
        List<StockRecord> lowAttempts = sortedByStrength.stream()
                .filter(stock -> parseBoardHeight(stock.boardHeight()) <= 1)
                .limit(4)
                .toList();

        int firstLimitFocus = safeMap(report.firstLimitSectorFocus()).getOrDefault(themeName, 0);
        double sectorHeat = safeList(report.topUpSectors()).stream()
                .filter(sector -> normalize(sector.name()).contains(normalize(themeName)) || normalize(themeName).contains(normalize(sector.name())))
                .map(SectorRecord::changePercent)
                .map(this::parsePercent)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(0D);
        int maxBoardHeight = matched.stream().mapToInt(stock -> parseBoardHeight(stock.boardHeight())).max().orElse(0);
        int limitUpCount = (int) matched.stream().filter(stock -> parseBoardHeight(stock.boardHeight()) >= 1).count();
        int brokenCount = (int) matched.stream().filter(stock -> parsePercent(stock.changePercent()) != null && parsePercent(stock.changePercent()) < 0).count();

        double themeScore = round2(firstLimitFocus * 14
                + highBoardStocks.size() * 12
                + coreStocks.size() * 7
                + Math.min(18, sectorHeat * 3)
                + Math.min(10, safeList(matchContext.relatedNews()).size() * 3));

        String themeStatus = inferStatus(themeScore, maxBoardHeight, limitUpCount, brokenCount, safeList(matchContext.relatedNews()).size());
        List<String> catalysts = buildCatalysts(themeName, matchContext, report);
        List<String> evidence = buildEvidence(themeName, themeScore, maxBoardHeight, limitUpCount, catalysts, coreStocks);
        List<String> counterEvidence = buildCounterEvidence(brokenCount, maxBoardHeight, midFollowers, catalysts);
        List<String> riskSignals = buildRiskSignals(themeStatus, highBoardStocks, midFollowers, lowAttempts);
        List<String> checkpoints = buildCheckpoints(themeStatus, highBoardStocks, coreStocks, lowAttempts);
        String verdict = buildVerdict(themeName, themeStatus, coreStocks, catalysts);
        double confidence = round2(Math.min(0.92, 0.35 + evidence.size() * 0.08 + Math.min(0.18, coreStocks.size() * 0.05)));

        return new ThemeSnapshot(
                themeName,
                report.tradeDate().toString(),
                themeStatus,
                themeScore,
                verdict,
                catalysts,
                evidence,
                counterEvidence,
                toStocks(coreStocks, "核心中军", themeName, matchContext.relatedStockNames()),
                toStocks(highBoardStocks, "龙头/高标", themeName, matchContext.relatedStockNames()),
                toStocks(midFollowers, "中位接力", themeName, matchContext.relatedStockNames()),
                toStocks(lowAttempts, "低位补涨/试错", themeName, matchContext.relatedStockNames()),
                riskSignals,
                checkpoints,
                confidence
        );
    }

    private ThemeTrackingDetail toDetail(ThemeSnapshot snapshot, List<ThemeTrackingHistoryItem> history) {
        return new ThemeTrackingDetail(
                snapshot.themeName(),
                snapshot.tradeDate(),
                snapshot.themeCatalysts(),
                snapshot.themeStatus(),
                snapshot.themeScore(),
                snapshot.verdict(),
                snapshot.evidenceList(),
                snapshot.counterEvidence(),
                snapshot.coreStocks(),
                snapshot.highBoardStocks(),
                snapshot.midLevelFollowers(),
                snapshot.lowLevelAttempts(),
                snapshot.riskSignals(),
                snapshot.nextDayCheckpoints(),
                history,
                snapshot.confidence(),
                aiGatewayClient.isConfigured(),
                false,
                aiProperties.provider(),
                aiProperties.model(),
                aiGatewayClient.isConfigured() ? DEFAULT_DISCLAIMER : DISABLED_DISCLAIMER,
                OffsetDateTime.now()
        );
    }

    private ThemeTrackingDetail maybeEnrichWithAi(ThemeTrackingDetail detail,
                                                  DailyRecapReport report,
                                                  MarketIndicators indicators,
                                                  MarketIntelligence intelligence) {
        if (!aiGatewayClient.isConfigured()) {
            return detail;
        }
        try {
            JsonNode requestBody = aiGatewayClient.newChatRequest(objectMapper.createArrayNode()
                    .add(objectMapper.createObjectNode()
                            .put("role", "system")
                            .put("content", """
                                    You are organizing A-share theme tracking evidence.
                                    Return one JSON object only.
                                    Do not give trade advice.
                                    Use the provided evidence and rewrite it into concise structured fields.
                                    """))
                    .add(objectMapper.createObjectNode()
                            .put("role", "user")
                            .put("content", buildAiPrompt(detail, report, indicators, intelligence))));
            String content = aiGatewayClient.chatCompletion(requestBody);
            return mergeAiDetail(detail, content);
        } catch (Exception ex) {
            return detail;
        }
    }

    private String buildAiPrompt(ThemeTrackingDetail detail,
                                 DailyRecapReport report,
                                 MarketIndicators indicators,
                                 MarketIntelligence intelligence) throws IOException {
        ArrayNode coreStocks = objectMapper.valueToTree(detail.coreStocks());
        ArrayNode highBoardStocks = objectMapper.valueToTree(detail.highBoardStocks());
        return """
                Schema:
                {
                  "themeStatus": "启动/发酵/加速/分歧/修复/退潮",
                  "verdict": "一句话结论",
                  "evidenceList": ["2-4条支持证据"],
                  "counterEvidence": ["1-3条反证"],
                  "themeCatalysts": ["1-3条催化摘要"],
                  "nextDayCheckpoints": ["2-4条次日验证点"],
                  "confidence": 0.0
                }

                Theme detail:
                %s

                Core stocks:
                %s

                High board stocks:
                %s

                Indicators:
                %s

                Intelligence topics:
                %s
                """.formatted(
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(detail),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(coreStocks),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(highBoardStocks),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(indicators),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(intelligence.topicPulses())
        );
    }

    private ThemeTrackingDetail mergeAiDetail(ThemeTrackingDetail detail, String content) throws IOException {
        JsonNode root = objectMapper.readTree(AiJsonSupport.extractJsonObject(content));
        return new ThemeTrackingDetail(
                detail.themeName(),
                detail.tradeDate(),
                pickStringList(root.path("themeCatalysts"), detail.themeCatalysts(), 3),
                AiJsonSupport.firstNonBlank(AiJsonSupport.text(root, "themeStatus"), detail.themeStatus()),
                detail.themeScore(),
                AiJsonSupport.firstNonBlank(AiJsonSupport.text(root, "verdict"), detail.verdict()),
                pickStringList(root.path("evidenceList"), detail.evidenceList(), 4),
                pickStringList(root.path("counterEvidence"), detail.counterEvidence(), 3),
                detail.coreStocks(),
                detail.highBoardStocks(),
                detail.midLevelFollowers(),
                detail.lowLevelAttempts(),
                detail.riskSignals(),
                pickStringList(root.path("nextDayCheckpoints"), detail.nextDayCheckpoints(), 4),
                detail.history(),
                clampConfidence(root.path("confidence").asDouble(detail.confidence())),
                true,
                true,
                aiProperties.provider(),
                aiProperties.model(),
                DEFAULT_DISCLAIMER,
                OffsetDateTime.now()
        );
    }

    private List<String> pickStringList(JsonNode node, List<String> fallback, int maxSize) {
        List<String> parsed = AiJsonSupport.readStringList(node, maxSize);
        return parsed.isEmpty() ? fallback : parsed;
    }

    private double clampConfidence(double value) {
        return round2(Math.max(0.05, Math.min(0.95, value)));
    }

    private List<ThemeTrackingHistoryItem> buildHistory(String themeName,
                                                        List<DailyRecapReport> recentReports,
                                                        DailyRecapReport currentReport) {
        List<ThemeTrackingHistoryItem> history = new ArrayList<>();
        DailyRecapReport previous = null;
        for (DailyRecapReport report : recentReports) {
            MarketIntelligence intelligence = report.tradeDate().equals(currentReport.tradeDate())
                    ? marketIntelligenceService.loadOrCollect(report.tradeDate(), false)
                    : loadHistoricalIntelligence(report.tradeDate());
            MarketIndicators indicators = indicatorService.calculate(report, recapStorageService.loadRecent(report.tradeDate(), 5));
            TradePlan tradePlan = tradePlanService.buildPlan(report, indicators);
            ThemeSnapshot snapshot = buildRuleSnapshot(themeName, report, intelligence, tradePlan);
            if (snapshot.themeScore() <= 5) {
                previous = report;
                continue;
            }
            history.add(new ThemeTrackingHistoryItem(
                    snapshot.tradeDate(),
                    snapshot.themeStatus(),
                    snapshot.themeScore(),
                    maxBoardHeight(snapshot.coreStocks(), snapshot.highBoardStocks()),
                    snapshot.coreStocks().stream().findFirst().map(ThemeTrackingStock::name).orElse("-"),
                    snapshot.verdict(),
                    validationStatus(previous, report, themeName)
            ));
            previous = report;
        }
        return history.stream().sorted(Comparator.comparing(ThemeTrackingHistoryItem::tradeDate).reversed()).limit(10).toList();
    }

    private MarketIntelligence loadHistoricalIntelligence(LocalDate tradeDate) {
        try {
            return marketIntelligenceService.loadOrCollect(tradeDate, false);
        } catch (Exception ex) {
            return new MarketIntelligence(tradeDate.toString(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    private String validationStatus(DailyRecapReport previous, DailyRecapReport current, String themeName) {
        if (previous == null) {
            return "待跟踪";
        }
        int prevHeight = maxBoardHeight(filterByTheme(previous, themeName));
        int currentHeight = maxBoardHeight(filterByTheme(current, themeName));
        if (currentHeight > prevHeight) {
            return "强化";
        }
        if (currentHeight == prevHeight) {
            return "延续";
        }
        return "转弱";
    }

    private List<StockRecord> filterByTheme(DailyRecapReport report, String themeName) {
        ThemeMatchContext context = buildMatchContext(themeName, loadHistoricalIntelligence(report.tradeDate()));
        List<StockRecord> allStocks = new ArrayList<>();
        allStocks.addAll(safeList(report.limitUpToday()));
        allStocks.addAll(safeList(report.firstLimitToday()));
        allStocks.addAll(safeList(report.brokenLimitToday()));
        return allStocks.stream().filter(stock -> matchesTheme(stock, context)).toList();
    }

    private int maxBoardHeight(Collection<StockRecord> stocks) {
        return stocks.stream().mapToInt(stock -> parseBoardHeight(stock.boardHeight())).max().orElse(0);
    }

    private int maxBoardHeight(List<ThemeTrackingStock> coreStocks, List<ThemeTrackingStock> highBoardStocks) {
        int max = 0;
        for (ThemeTrackingStock stock : coreStocks) {
            max = Math.max(max, parseBoardHeight(stock.boardHeight()));
        }
        for (ThemeTrackingStock stock : highBoardStocks) {
            max = Math.max(max, parseBoardHeight(stock.boardHeight()));
        }
        return max;
    }

    private ThemeMatchContext buildMatchContext(String themeName, MarketIntelligence intelligence) {
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add(themeName);
        for (String part : themeName.split("[/、,\\s]+")) {
            if (!part.isBlank()) {
                aliases.add(part.trim());
            }
        }
        List<String> relatedStocks = new ArrayList<>();
        List<String> relatedNews = new ArrayList<>();
        safeList(intelligence.themeClusters()).forEach(cluster -> {
            if (fuzzyContains(cluster.name(), themeName) || fuzzyContains(themeName, cluster.name())) {
                aliases.add(cluster.name());
                relatedStocks.addAll(safeList(cluster.relatedStocks()));
                relatedNews.addAll(safeList(cluster.sampleTitles()));
            }
        });
        safeList(intelligence.marketNews()).forEach(item -> {
            if (fuzzyContains(item.title(), themeName) || fuzzyContains(item.summary(), themeName)) {
                relatedNews.add(item.title());
            }
        });
        safeList(intelligence.stockNews()).forEach(item -> {
            if (fuzzyContains(item.title(), themeName) || fuzzyContains(item.summary(), themeName)) {
                relatedNews.add(item.title());
            }
        });
        return new ThemeMatchContext(themeName, aliases, relatedStocks, relatedNews);
    }

    private List<String> buildCatalysts(String themeName, ThemeMatchContext context, DailyRecapReport report) {
        LinkedHashSet<String> catalysts = new LinkedHashSet<>();
        if (!context.relatedNews().isEmpty()) {
            catalysts.add("新闻催化聚焦 " + truncate(context.relatedNews().get(0), 28));
        }
        safeList(report.topUpSectors()).stream()
                .filter(sector -> fuzzyContains(sector.name(), themeName) || fuzzyContains(themeName, sector.name()))
                .findFirst()
                .ifPresent(sector -> catalysts.add("板块涨幅前列，强度 " + safeText(sector.changePercent())));
        int firstLimit = safeMap(report.firstLimitSectorFocus()).getOrDefault(themeName, 0);
        if (firstLimit > 0) {
            catalysts.add("首板扩散数量 " + firstLimit + " 只");
        }
        return catalysts.stream().limit(3).toList();
    }

    private List<String> buildEvidence(String themeName,
                                       double themeScore,
                                       int maxBoardHeight,
                                       int limitUpCount,
                                       List<String> catalysts,
                                       List<StockRecord> coreStocks) {
        List<String> evidence = new ArrayList<>();
        evidence.add(themeName + " 主题分数 " + themeScore + "，处于最近主线候选前列");
        if (maxBoardHeight >= 3) {
            evidence.add("高标高度达到 " + maxBoardHeight + " 板，仍有龙头辨识度");
        }
        if (limitUpCount >= 2) {
            evidence.add("板块内可见 " + limitUpCount + " 只涨停/首板样本，说明梯队未断");
        }
        if (!catalysts.isEmpty()) {
            evidence.add(catalysts.get(0));
        }
        if (!coreStocks.isEmpty()) {
            evidence.add("核心观察票包括 " + coreStocks.stream().map(StockRecord::name).limit(3).collect(Collectors.joining("、")));
        }
        return evidence.stream().limit(4).toList();
    }

    private List<String> buildCounterEvidence(int brokenCount,
                                              int maxBoardHeight,
                                              List<StockRecord> midFollowers,
                                              List<String> catalysts) {
        List<String> counterEvidence = new ArrayList<>();
        if (brokenCount >= 2) {
            counterEvidence.add("板块内负反馈样本偏多，分歧正在放大");
        }
        if (maxBoardHeight <= 1) {
            counterEvidence.add("高标高度不足，主线辨识度还不够稳定");
        }
        if (midFollowers.isEmpty()) {
            counterEvidence.add("中位承接偏弱，容易出现高标单点强势");
        }
        if (catalysts.isEmpty()) {
            counterEvidence.add("缺少明确外部催化，只能靠盘口强度维持");
        }
        return counterEvidence.stream().limit(3).toList();
    }

    private List<String> buildRiskSignals(String themeStatus,
                                          List<StockRecord> highBoardStocks,
                                          List<StockRecord> midFollowers,
                                          List<StockRecord> lowAttempts) {
        List<String> riskSignals = new ArrayList<>();
        if ("分歧".equals(themeStatus) || "退潮".equals(themeStatus)) {
            riskSignals.add("高位一致性减弱，追高容错率明显下降");
        }
        if (highBoardStocks.isEmpty()) {
            riskSignals.add("没有稳定高标领涨，题材可能偏轮动而非主升");
        }
        if (midFollowers.isEmpty()) {
            riskSignals.add("中位股承接不足，主线延续性要打折");
        }
        if (lowAttempts.size() >= 3 && highBoardStocks.isEmpty()) {
            riskSignals.add("更多是低位试错，未必能直接走成主线");
        }
        return riskSignals.stream().limit(4).toList();
    }

    private List<String> buildCheckpoints(String themeStatus,
                                          List<StockRecord> highBoardStocks,
                                          List<StockRecord> coreStocks,
                                          List<StockRecord> lowAttempts) {
        List<String> checkpoints = new ArrayList<>();
        if (!highBoardStocks.isEmpty()) {
            checkpoints.add("观察高标 " + highBoardStocks.get(0).name() + " 是否继续走强并带动板块扩散");
        }
        if (!coreStocks.isEmpty()) {
            checkpoints.add("观察核心票是否出现缩量加速或放量承接，而不是爆量滞涨");
        }
        if (!lowAttempts.isEmpty()) {
            checkpoints.add("观察低位补涨是否能晋级，确认主线是否还有扩散能力");
        }
        if ("分歧".equals(themeStatus)) {
            checkpoints.add("若高标修复失败且中位股继续掉队，则按转弱处理");
        } else if ("加速".equals(themeStatus)) {
            checkpoints.add("若高标一字或缩量加速过度，防止次日一致转分歧");
        } else {
            checkpoints.add("优先看板块是否继续站稳前排，而不是只靠单票冲高");
        }
        return checkpoints.stream().limit(4).toList();
    }

    private String buildVerdict(String themeName, String themeStatus, List<StockRecord> coreStocks, List<String> catalysts) {
        String stockText = coreStocks.isEmpty()
                ? "核心票待确认"
                : coreStocks.stream().map(StockRecord::name).limit(2).collect(Collectors.joining("、"));
        if (!catalysts.isEmpty()) {
            return themeName + " 当前处于" + themeStatus + "，催化仍在，优先跟踪 " + stockText;
        }
        return themeName + " 当前处于" + themeStatus + "，主要依靠盘口强度，优先跟踪 " + stockText;
    }

    private String inferStatus(double score, int maxBoardHeight, int limitUpCount, int brokenCount, int catalystCount) {
        if (score >= 75 && maxBoardHeight >= 4 && brokenCount <= 1) {
            return "加速";
        }
        if (score >= 58 && maxBoardHeight >= 2 && catalystCount >= 1) {
            return "发酵";
        }
        if (score >= 45 && brokenCount >= 2) {
            return "分歧";
        }
        if (score >= 35 && maxBoardHeight >= 2 && brokenCount <= 1) {
            return "修复";
        }
        if (score >= 22 && limitUpCount >= 1) {
            return "启动";
        }
        return "退潮";
    }

    private List<ThemeTrackingStock> toStocks(List<StockRecord> stocks,
                                              String defaultRole,
                                              String themeName,
                                              List<String> relatedStockNames) {
        return stocks.stream()
                .map(stock -> new ThemeTrackingStock(
                        stock.code(),
                        stock.name(),
                        deriveRole(stock, defaultRole, relatedStockNames),
                        behaviorTag(stock),
                        safeText(stock.boardHeight()),
                        safeText(stock.changePercent()),
                        safeText(stock.amount()),
                        safeText(stock.reason()),
                        buildObservation(stock, themeName)
                ))
                .toList();
    }

    private String deriveRole(StockRecord stock, String defaultRole, List<String> relatedStockNames) {
        if (relatedStockNames.stream().anyMatch(name -> safeText(stock.name()).contains(name))) {
            return "龙头";
        }
        int boardHeight = parseBoardHeight(stock.boardHeight());
        if (boardHeight >= 4) {
            return "龙头/最高标";
        }
        if (boardHeight >= 2) {
            return "高标助攻";
        }
        return defaultRole;
    }

    private String behaviorTag(StockRecord stock) {
        Double change = parsePercent(stock.changePercent());
        Double turnover = parsePercent(stock.turnoverRate());
        Double amplitude = parsePercent(stock.amplitude());
        if (change == null) {
            return "待观察";
        }
        if (change >= 9.5 && turnover != null && turnover < 15) {
            return "缩量加速";
        }
        if (change >= 6 && amplitude != null && amplitude >= 10) {
            return "放量分歧";
        }
        if (change <= 0 && amplitude != null && amplitude >= 8) {
            return "强转弱";
        }
        if (change > 0 && turnover != null && turnover >= 20) {
            return "换手承接";
        }
        return "正常演化";
    }

    private String buildObservation(StockRecord stock, String themeName) {
        String tag = behaviorTag(stock);
        int boardHeight = parseBoardHeight(stock.boardHeight());
        if (boardHeight >= 2) {
            return themeName + " 内处于高位，重点看 " + tag + " 是否继续强化";
        }
        return "关注 " + tag + " 是否能转化为晋级或补涨";
    }

    private List<StockRecord> dedupeStocks(List<StockRecord> stocks) {
        Map<String, StockRecord> map = new LinkedHashMap<>();
        for (StockRecord stock : stocks) {
            map.putIfAbsent(stock.code() + "-" + stock.name(), stock);
        }
        return new ArrayList<>(map.values());
    }

    private boolean matchesTheme(StockRecord stock, ThemeMatchContext context) {
        String haystack = String.join(" ",
                safeText(stock.industry()),
                safeText(stock.concept()),
                safeText(stock.reason()),
                safeText(stock.name()));
        for (String alias : context.aliases()) {
            if (fuzzyContains(haystack, alias) || fuzzyContains(alias, haystack)) {
                return true;
            }
        }
        return context.relatedStockNames().stream().anyMatch(name -> safeText(stock.name()).contains(name));
    }

    private double stockStrength(StockRecord stock, List<String> relatedStockNames) {
        double score = 0;
        int boardHeight = parseBoardHeight(stock.boardHeight());
        score += boardHeight * 12;
        Double change = parsePercent(stock.changePercent());
        if (change != null) {
            score += Math.max(0, change);
        }
        Double turnover = parsePercent(stock.turnoverRate());
        if (turnover != null) {
            score += Math.min(12, turnover / 2);
        }
        if (relatedStockNames.stream().anyMatch(name -> safeText(stock.name()).contains(name))) {
            score += 15;
        }
        return score;
    }

    private String cleanThemeName(String value) {
        return safeText(value).replace("概念", "").replace("板块", "").trim();
    }

    private boolean fuzzyContains(String source, String target) {
        String normalizedSource = normalize(source);
        String normalizedTarget = normalize(target);
        return !normalizedSource.isBlank() && !normalizedTarget.isBlank() && normalizedSource.contains(normalizedTarget);
    }

    private String normalize(String value) {
        return safeText(value).toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private String truncate(String value, int max) {
        String text = safeText(value);
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private Double parsePercent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.replace("%", "").trim();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int parseBoardHeight(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static <K, V> Map<K, V> safeMap(Map<K, V> map) {
        return map == null ? Map.of() : map;
    }

    private record ThemeMatchContext(
            String themeName,
            Set<String> aliases,
            List<String> relatedStockNames,
            List<String> relatedNews
    ) {}

    private record ThemeSnapshot(
            String themeName,
            String tradeDate,
            String themeStatus,
            double themeScore,
            String verdict,
            List<String> themeCatalysts,
            List<String> evidenceList,
            List<String> counterEvidence,
            List<ThemeTrackingStock> coreStocks,
            List<ThemeTrackingStock> highBoardStocks,
            List<ThemeTrackingStock> midLevelFollowers,
            List<ThemeTrackingStock> lowLevelAttempts,
            List<String> riskSignals,
            List<String> nextDayCheckpoints,
            double confidence
    ) {}
}
