package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meirifupan.backend.config.AiProperties;
import com.meirifupan.backend.model.StockAiAnalysisResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class StockAiAnalysisService {

    private static final String DISCLAIMER = "仅用于量价关系与裸K学习，不构成投资建议。分钟级数据更适合训练节奏感，不适合机械跟单。";
    private static final String CACHE_SCENARIO = "stock-ai-analysis:v1";

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final AiGatewayClient aiGatewayClient;
    private final AiRequestCacheService aiRequestCacheService;

    public StockAiAnalysisService(AiProperties aiProperties,
                                  ObjectMapper objectMapper,
                                  AiGatewayClient aiGatewayClient,
                                  AiRequestCacheService aiRequestCacheService) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.aiGatewayClient = aiGatewayClient;
        this.aiRequestCacheService = aiRequestCacheService;
    }

    public StockAiAnalysisResponse analyze(MultipartFile file, String timeframe, String stockCode, String stockName) {
        JsonNode stockData = parseUploadedBars(file, timeframe, stockCode, stockName);
        if (!aiEnabled()) {
            return buildFallbackResponse(stockData, "disabled");
        }

        try {
            String cacheKey = aiRequestCacheService.buildCacheKey(CACHE_SCENARIO, stockData.toString());
            var cached = aiRequestCacheService.load(cacheKey, StockAiAnalysisResponse.class);
            if (cached.isPresent()) {
                return cached.get();
            }
            JsonNode aiRoot = requestAiAnalysis(stockData);
            StockAiAnalysisResponse response = mergeAiResponse(stockData, aiRoot, "ready");
            aiRequestCacheService.save(cacheKey, CACHE_SCENARIO, response);
            return response;
        } catch (Exception ex) {
            return buildFallbackResponse(stockData, "error");
        }
    }

    private JsonNode parseUploadedBars(MultipartFile file, String timeframe, String stockCode, String stockName) {
        String normalizedTimeframe = normalizeTimeframe(timeframe);
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lowerName = filename.toLowerCase(Locale.ROOT);
        List<UploadedBar> bars = lowerName.endsWith(".json")
                ? parseJsonBars(file)
                : parseDelimitedBars(file);
        if (bars.isEmpty()) {
            throw new IllegalArgumentException("上传文件里没有识别到有效K线数据，请检查列名或内容格式");
        }
        bars.sort(Comparator.comparing(UploadedBar::time));

        String resolvedCode = sanitizeStockCode(stockCode);
        String resolvedName = trimToEmpty(stockName);
        if (resolvedCode.isBlank()) {
            for (UploadedBar bar : bars) {
                if (!bar.stockCode().isBlank()) {
                    resolvedCode = sanitizeStockCode(bar.stockCode());
                    break;
                }
            }
        }
        if (resolvedName.isBlank()) {
            for (UploadedBar bar : bars) {
                if (!bar.stockName().isBlank()) {
                    resolvedName = trimToEmpty(bar.stockName());
                    break;
                }
            }
        }
        if (resolvedCode.isBlank()) {
            resolvedCode = inferCodeFromFilename(filename);
        }
        if (resolvedName.isBlank()) {
            resolvedName = inferNameFromFilename(filename);
        }

        if (resolvedCode.isBlank()) {
            resolvedCode = "UPLOAD";
        }
        if (resolvedName.isBlank()) {
            resolvedName = resolvedCode;
        }

        ArrayList<StockAiAnalysisResponse.CandleBar> candleBars = new ArrayList<>();
        for (UploadedBar item : bars) {
            candleBars.add(new StockAiAnalysisResponse.CandleBar(
                    item.time(),
                    item.open(),
                    item.close(),
                    item.high(),
                    item.low(),
                    item.volume(),
                    item.amount(),
                    item.changePercent(),
                    item.amplitudePercent()
            ));
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("stockCode", resolvedCode);
        root.put("stockName", resolvedName);
        root.put("timeframe", normalizedTimeframe);
        root.put("timeframeLabel", timeframeLabel(normalizedTimeframe));
        root.put("source", "upload");
        root.put("analyzedBars", candleBars.size());
        root.set("signals", objectMapper.valueToTree(buildSignals(candleBars)));
        root.set("metrics", summarizeBars(candleBars));
        root.set("bars", objectMapper.valueToTree(candleBars));
        return root;
    }

    private JsonNode requestAiAnalysis(JsonNode stockData) throws IOException, InterruptedException {
        String prompt = """
                你是A股裸K与量价关系分析助手。
                目标不是预测涨跌，而是帮助用户学习买卖点。
                你只能基于给定K线与成交量数据分析，不要引用基本面、消息面、龙虎榜、政策预期。
                重点观察：
                1. 趋势延续还是衰竭
                2. 放量突破、缩量回踩、放量滞涨、放量破位、长上影、长下影、收盘位置
                3. 最近一段时间适合等待、低吸确认、突破跟随、减仓兑现还是回避
                4. 输出必须强调“学习参考，不构成实盘建议”

                只输出JSON，不要markdown代码块，格式如下：
                {
                  "headline": "一句话结论",
                  "summary": "1段总结",
                  "trendBias": "偏多/震荡偏多/震荡/震荡偏空/偏空",
                  "actionBias": "等待确认/逢回踩观察/突破后跟随/分批止盈/谨慎回避",
                  "confidence": "低/中/高",
                  "volumePriceSignals": ["量价结构信号"],
                  "buyPoints": ["偏学习视角的买点条件"],
                  "sellPoints": ["偏学习视角的卖点条件"],
                  "learningPoints": ["用户可以复盘学习的重点"],
                  "riskWarnings": ["风险提醒"]
                }
                以下是数据：
                """ + stockData.toPrettyString();

        JsonNode requestBody = aiGatewayClient.newChatRequest(objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "system")
                                .put("content", "你是严格遵守JSON输出要求的A股量价分析助手。"))
                        .add(objectMapper.createObjectNode()
                                .put("role", "user")
                                .put("content", prompt)));
        String content = aiGatewayClient.chatCompletion(requestBody);
        return objectMapper.readTree(AiJsonSupport.extractJsonObject(content));
    }

    private StockAiAnalysisResponse buildFallbackResponse(JsonNode stockData, String status) {
        JsonNode metrics = stockData.path("metrics");
        double change = metrics.path("periodChangePercent").asDouble(0.0);
        double volumeRatio = metrics.path("recentVolumeRatio").asDouble(1.0);
        String trendBias = change > 3 ? "偏多" : change > 0.8 ? "震荡偏多" : change < -3 ? "偏空" : change < -0.8 ? "震荡偏空" : "震荡";
        String actionBias = volumeRatio > 1.25 && change > 0 ? "突破后跟随" : change > 0 ? "逢回踩观察" : change < 0 ? "等待确认" : "谨慎回避";
        String headline = "%s %s量价学习观察".formatted(AiJsonSupport.text(stockData, "stockName"), AiJsonSupport.text(stockData, "timeframeLabel"));
        String summary = "当前窗口涨跌幅约 %.2f%%，最近量能比约 %.2f。更适合把它当成量价教学样本，先观察价格是否延续、回踩是否缩量，再决定是否跟随。"
                .formatted(change, volumeRatio);
        return buildResponse(
                stockData,
                headline,
                summary,
                trendBias,
                actionBias,
                status.equals("disabled") ? "低" : "中",
                defaultIfEmpty(AiJsonSupport.readStringList(stockData.path("signals"), 8), List.of("当前数据可用于基础量价观察。")),
                List.of(
                        "等待价格重新站回最近一段时间的短线高点，同时成交量温和放大。",
                        "上涨后回踩如果量能收缩、低点不破，可作为观察承接是否有效的学习点。"
                ),
                List.of(
                        "冲高后收盘重新回到区间内部，且量能明显放大时，通常说明追价性价比下降。",
                        "跌破前一个关键回踩低点且放量时，说明短线结构转弱。"
                ),
                List.of(
                        "先看收盘位置，再看成交量是否配合，而不是只看涨跌幅。",
                        "把这段走势拆成启动、确认、加速、分歧四个阶段去看。"
                ),
                List.of(
                        "分钟级别噪音很大，越短周期越容易出现假突破和假跌破。",
                        "AI 结论更适合作为复盘提纲，不适合作为下单指令。"
                ),
                status
        );
    }

    private StockAiAnalysisResponse mergeAiResponse(JsonNode stockData, JsonNode aiRoot, String status) {
        return buildResponse(
                stockData,
                AiJsonSupport.firstNonBlank(AiJsonSupport.text(aiRoot, "headline"), AiJsonSupport.text(stockData, "stockName") + " " + AiJsonSupport.text(stockData, "timeframeLabel") + " 量价分析"),
                AiJsonSupport.firstNonBlank(AiJsonSupport.text(aiRoot, "summary"), "AI 未返回总结。"),
                AiJsonSupport.firstNonBlank(AiJsonSupport.text(aiRoot, "trendBias"), "震荡"),
                AiJsonSupport.firstNonBlank(AiJsonSupport.text(aiRoot, "actionBias"), "等待确认"),
                AiJsonSupport.firstNonBlank(AiJsonSupport.text(aiRoot, "confidence"), "中"),
                AiJsonSupport.readStringList(aiRoot.path("volumePriceSignals"), 8),
                AiJsonSupport.readStringList(aiRoot.path("buyPoints"), 8),
                AiJsonSupport.readStringList(aiRoot.path("sellPoints"), 8),
                AiJsonSupport.readStringList(aiRoot.path("learningPoints"), 8),
                AiJsonSupport.readStringList(aiRoot.path("riskWarnings"), 8),
                status
        );
    }

    private StockAiAnalysisResponse buildResponse(
            JsonNode stockData,
            String headline,
            String summary,
            String trendBias,
            String actionBias,
            String confidence,
            List<String> volumePriceSignals,
            List<String> buyPoints,
            List<String> sellPoints,
            List<String> learningPoints,
            List<String> riskWarnings,
            String status
    ) {
        JsonNode metrics = stockData.path("metrics");
        return new StockAiAnalysisResponse(
                aiEnabled(),
                aiProperties.provider(),
                aiProperties.model(),
                status,
                AiJsonSupport.text(stockData, "stockCode"),
                AiJsonSupport.text(stockData, "stockName"),
                AiJsonSupport.text(stockData, "timeframe"),
                AiJsonSupport.text(stockData, "timeframeLabel"),
                AiJsonSupport.text(stockData, "source"),
                stockData.path("analyzedBars").asInt(),
                AiJsonSupport.text(metrics, "windowStart"),
                AiJsonSupport.text(metrics, "windowEnd"),
                nullableDouble(metrics.path("latestPrice")),
                nullableDouble(metrics.path("periodChangePercent")),
                nullableDouble(metrics.path("rangeHigh")),
                nullableDouble(metrics.path("rangeLow")),
                nullableDouble(metrics.path("averageVolume")),
                nullableDouble(metrics.path("recentVolumeRatio")),
                headline,
                summary,
                trendBias,
                actionBias,
                confidence,
                defaultIfEmpty(volumePriceSignals, AiJsonSupport.readStringList(stockData.path("signals"), 8)),
                defaultIfEmpty(buyPoints, List.of("等待价格与成交量共同确认，不做单根K线的主观猜顶抄底。")),
                defaultIfEmpty(sellPoints, List.of("一旦出现放量冲高回落或放量破位，优先从保护利润与回撤控制角度审视。")),
                defaultIfEmpty(learningPoints, List.of("把每一段走势拆成启动、确认、加速、分歧四个阶段去看。")),
                defaultIfEmpty(riskWarnings, List.of("AI 结论只适合作为复盘提纲，不适合作为下单指令。")),
                readCandles(stockData.path("bars")),
                DISCLAIMER,
                OffsetDateTime.now()
        );
    }

    private List<StockAiAnalysisResponse.CandleBar> readCandles(JsonNode node) {
        List<StockAiAnalysisResponse.CandleBar> result = new ArrayList<>();
        if (!node.isArray()) {
            return result;
        }
        int start = Math.max(0, node.size() - 60);
        for (int i = start; i < node.size(); i++) {
            JsonNode item = node.get(i);
            result.add(new StockAiAnalysisResponse.CandleBar(
                    AiJsonSupport.text(item, "time"),
                    item.path("open").asDouble(),
                    item.path("close").asDouble(),
                    item.path("high").asDouble(),
                    item.path("low").asDouble(),
                    item.path("volume").asDouble(),
                    item.path("amount").asDouble(),
                    nullableDouble(item.path("changePercent")),
                    nullableDouble(item.path("amplitudePercent"))
            ));
        }
        return result;
    }

    private boolean aiEnabled() {
        return aiGatewayClient.isConfigured();
    }

    private List<UploadedBar> parseJsonBars(MultipartFile file) {
        try {
            JsonNode root = objectMapper.readTree(file.getInputStream());
            JsonNode rows = root;
            if (root.isObject()) {
                rows = root.path("bars").isArray() ? root.path("bars") : root.path("data");
            }
            if (!rows.isArray()) {
                throw new IllegalArgumentException("JSON 文件必须是数组，或包含 bars/data 数组");
            }
            List<UploadedBar> result = new ArrayList<>();
            for (JsonNode node : rows) {
                UploadedBar item = toUploadedBar(jsonToMap(node));
                if (item != null) {
                    result.add(item);
                }
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法解析 JSON 历史数据文件", ex);
        }
    }

    private List<UploadedBar> parseDelimitedBars(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String firstLine = reader.readLine();
            if (firstLine == null) {
                return List.of();
            }
            String delimiter = detectDelimiter(firstLine);
            List<String> headers = parseDelimitedLine(firstLine, delimiter.charAt(0));
            Map<Integer, String> headerMap = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                headerMap.put(i, normalizeHeader(headers.get(i)));
            }
            List<UploadedBar> result = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseDelimitedLine(line, delimiter.charAt(0));
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < values.size(); i++) {
                    String key = headerMap.get(i);
                    if (key != null) {
                        row.put(key, values.get(i));
                    }
                }
                UploadedBar item = toUploadedBar(row);
                if (item != null) {
                    result.add(item);
                }
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法读取上传的历史数据文件", ex);
        }
    }

    private Map<String, Object> jsonToMap(JsonNode node) {
        Map<String, Object> result = new HashMap<>();
        node.fields().forEachRemaining(entry -> result.put(normalizeHeader(entry.getKey()), entry.getValue().asText()));
        return result;
    }

    private UploadedBar toUploadedBar(Map<String, ?> row) {
        String time = firstValue(row, Set.of("time", "datetime", "date", "trade_time", "trading_time", "时间", "日期"));
        Double open = parseDouble(firstValue(row, Set.of("open", "openprice", "开盘", "开盘价")));
        Double high = parseDouble(firstValue(row, Set.of("high", "最高", "最高价")));
        Double low = parseDouble(firstValue(row, Set.of("low", "最低", "最低价")));
        Double close = parseDouble(firstValue(row, Set.of("close", "收盘", "收盘价", "lastprice", "最新价")));
        Double volume = parseDouble(firstValue(row, Set.of("volume", "vol", "成交量", "成交总量")));
        Double amount = parseDouble(firstValue(row, Set.of("amount", "成交额", "成交金额")));
        if (time.isBlank() || open == null || high == null || low == null || close == null) {
            return null;
        }
        Double changePercent = parseDouble(firstValue(row, Set.of("changepercent", "pct_chg", "涨跌幅")));
        Double amplitudePercent = parseDouble(firstValue(row, Set.of("amplitudepercent", "amplitude", "振幅")));
        return new UploadedBar(
                normalizeTimeValue(time),
                open,
                high,
                low,
                close,
                volume == null ? 0.0 : volume,
                amount == null ? 0.0 : amount,
                trimToEmpty(firstValue(row, Set.of("stockcode", "code", "symbol", "股票代码", "证券代码"))),
                trimToEmpty(firstValue(row, Set.of("stockname", "name", "股票名称", "证券名称"))),
                changePercent,
                amplitudePercent
        );
    }

    private String firstValue(Map<String, ?> row, Set<String> aliases) {
        for (Map.Entry<String, ?> entry : row.entrySet()) {
            if (aliases.contains(normalizeHeader(entry.getKey()))) {
                return trimToEmpty(String.valueOf(entry.getValue()));
            }
        }
        return "";
    }

    private String detectDelimiter(String line) {
        if (line.contains("\t")) {
            return "\t";
        }
        if (line.contains(",")) {
            return ",";
        }
        return ",";
    }

    private List<String> parseDelimitedLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == delimiter && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString().trim());
        return values;
    }

    private String normalizeHeader(String value) {
        return trimToEmpty(value).toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private Double parseDouble(String value) {
        String text = trimToEmpty(value).replace(",", "").replace("%", "");
        if (text.isBlank() || text.equalsIgnoreCase("null") || text.equalsIgnoreCase("nan")) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeTimeValue(String value) {
        String text = trimToEmpty(value);
        if (text.length() >= 19) {
            return text.substring(0, 19).replace('T', ' ');
        }
        if (text.length() == 8 && text.chars().allMatch(Character::isDigit)) {
            return text;
        }
        return text.replace('T', ' ');
    }

    private String sanitizeStockCode(String value) {
        String digits = trimToEmpty(value).replaceAll("\\D", "");
        return digits.length() == 6 ? digits : "";
    }

    private String inferCodeFromFilename(String filename) {
        if (filename == null) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{6})").matcher(filename);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String inferNameFromFilename(String filename) {
        if (filename == null) {
            return "";
        }
        String base = filename.replaceFirst("\\.[^.]+$", "");
        return base.length() > 40 ? base.substring(0, 40) : base;
    }

    private String normalizeTimeframe(String value) {
        String text = trimToEmpty(value).toLowerCase(Locale.ROOT);
        if (Set.of("1", "5", "15", "30", "60", "day").contains(text)) {
            return text;
        }
        throw new IllegalArgumentException("仅支持 1/5/15/30/60/day 周期");
    }

    private String timeframeLabel(String timeframe) {
        return switch (timeframe) {
            case "1" -> "1分钟";
            case "5" -> "5分钟";
            case "15" -> "15分钟";
            case "30" -> "30分钟";
            case "60" -> "60分钟";
            default -> "日K";
        };
    }

    private ObjectNode summarizeBars(List<StockAiAnalysisResponse.CandleBar> bars) {
        if (bars.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        double rangeHigh = bars.stream().mapToDouble(StockAiAnalysisResponse.CandleBar::high).max().orElse(0.0);
        double rangeLow = bars.stream().mapToDouble(StockAiAnalysisResponse.CandleBar::low).min().orElse(0.0);
        double avgVolume = bars.stream().mapToDouble(StockAiAnalysisResponse.CandleBar::volume).average().orElse(0.0);
        int recentCount = Math.max(3, Math.min(12, bars.size() / 5 == 0 ? 1 : bars.size() / 5));
        List<StockAiAnalysisResponse.CandleBar> recent = bars.subList(Math.max(0, bars.size() - recentCount), bars.size());
        List<StockAiAnalysisResponse.CandleBar> previous = bars.subList(Math.max(0, bars.size() - recentCount * 2), Math.max(0, bars.size() - recentCount));
        double recentAvg = recent.stream().mapToDouble(StockAiAnalysisResponse.CandleBar::volume).average().orElse(0.0);
        double previousAvg = previous.isEmpty() ? recentAvg : previous.stream().mapToDouble(StockAiAnalysisResponse.CandleBar::volume).average().orElse(recentAvg);
        double ratio = previousAvg == 0 ? 1.0 : recentAvg / previousAvg;
        StockAiAnalysisResponse.CandleBar first = bars.get(0);
        StockAiAnalysisResponse.CandleBar latest = bars.get(bars.size() - 1);
        return objectMapper.createObjectNode()
                .put("windowStart", first.time())
                .put("windowEnd", latest.time())
                .put("latestPrice", latest.close())
                .put("periodChangePercent", first.open() == 0 ? 0.0 : ((latest.close() - first.open()) / first.open()) * 100)
                .put("rangeHigh", rangeHigh)
                .put("rangeLow", rangeLow)
                .put("averageVolume", avgVolume)
                .put("recentVolumeRatio", ratio);
    }

    private List<String> buildSignals(List<StockAiAnalysisResponse.CandleBar> bars) {
        if (bars.size() < 5) {
            return List.of("样本K线较少，先以区间高低点和量能变化做基础观察。");
        }
        StockAiAnalysisResponse.CandleBar latest = bars.get(bars.size() - 1);
        StockAiAnalysisResponse.CandleBar previous = bars.get(bars.size() - 2);
        List<StockAiAnalysisResponse.CandleBar> recent = bars.subList(Math.max(0, bars.size() - 5), bars.size());
        double recentHigh = recent.subList(0, recent.size() - 1).stream().mapToDouble(StockAiAnalysisResponse.CandleBar::high).max().orElse(latest.high());
        double recentLow = recent.subList(0, recent.size() - 1).stream().mapToDouble(StockAiAnalysisResponse.CandleBar::low).min().orElse(latest.low());
        double avgVolume = recent.subList(0, recent.size() - 1).stream().mapToDouble(StockAiAnalysisResponse.CandleBar::volume).average().orElse(latest.volume());
        List<String> signals = new ArrayList<>();
        if (latest.close() > recentHigh && latest.volume() > avgVolume * 1.2) {
            signals.add("最近一根K线放量突破短线区间高点，属于偏强的价格接受。");
        }
        if (latest.close() < recentLow && latest.volume() > avgVolume * 1.2) {
            signals.add("最近一根K线放量跌破短线区间低点，说明抛压释放更主动。");
        }
        if (latest.close() > latest.open() && latest.close() >= latest.high() - (latest.high() - latest.low()) * 0.2) {
            signals.add("K线收在高位区域，买方在本周期结束前保持了主导。");
        }
        if (latest.close() < latest.open() && latest.close() <= latest.low() + (latest.high() - latest.low()) * 0.2) {
            signals.add("K线收在低位区域，说明尾段承接偏弱。");
        }
        if (latest.high() > previous.high() && latest.close() < previous.close()) {
            signals.add("出现冲高回落迹象，追价的性价比下降，需要等待再次确认。");
        }
        if (latest.low() < previous.low() && latest.close() > previous.close()) {
            signals.add("下探后被快速收回，说明低位承接存在。");
        }
        return signals.isEmpty() ? List.of("当前更偏区间震荡，重点观察高低点是否被有效突破。") : signals;
    }

    private record UploadedBar(
            String time,
            double open,
            double high,
            double low,
            double close,
            double volume,
            double amount,
            String stockCode,
            String stockName,
            Double changePercent,
            Double amplitudePercent
    ) {
    }

    private Double nullableDouble(JsonNode node) {
        return node.isNumber() ? node.asDouble() : null;
    }

    private List<String> defaultIfEmpty(List<String> preferred, List<String> fallback) {
        return preferred == null || preferred.isEmpty() ? fallback : preferred;
    }

}
