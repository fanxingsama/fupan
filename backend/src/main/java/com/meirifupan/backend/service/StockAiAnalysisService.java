package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meirifupan.backend.config.AiProperties;
import com.meirifupan.backend.config.RecapProperties;
import com.meirifupan.backend.model.StockAiAnalysisRequest;
import com.meirifupan.backend.model.StockAiAnalysisResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class StockAiAnalysisService {

    private static final String DISCLAIMER = "仅用于量价关系与裸K学习，不构成投资建议。分钟级数据更适合训练节奏感，不适合机械跟单。";

    private final RecapProperties recapProperties;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public StockAiAnalysisService(RecapProperties recapProperties, AiProperties aiProperties, ObjectMapper objectMapper) {
        this.recapProperties = recapProperties;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public StockAiAnalysisResponse analyze(StockAiAnalysisRequest request) {
        JsonNode stockData = collectBars(request.stockCode(), request.timeframe());
        if (!aiEnabled()) {
            return buildFallbackResponse(stockData, "disabled");
        }

        try {
            JsonNode aiRoot = requestAiAnalysis(stockData);
            return mergeAiResponse(stockData, aiRoot, "ready");
        } catch (Exception ex) {
            return buildFallbackResponse(stockData, "error");
        }
    }

    private JsonNode collectBars(String stockCode, String timeframe) {
        Path scriptPath = Path.of("scripts", "collect_stock_bars.py").toAbsolutePath();
        if (!Files.exists(scriptPath)) {
            throw new IllegalStateException("Stock collector script not found: " + scriptPath);
        }

        List<String> command = new ArrayList<>();
        command.add(recapProperties.pythonExecutable());
        command.add("-X");
        command.add("utf8");
        command.add(scriptPath.toString());
        command.add("--code");
        command.add(stockCode);
        command.add("--timeframe");
        command.add(timeframe);

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
                throw new IllegalStateException("Stock collector failed: " + stderr);
            }
            return objectMapper.readTree(stdout);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to execute stock collector script.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stock collector interrupted.", e);
        }
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

        JsonNode requestBody = objectMapper.createObjectNode()
                .put("model", aiProperties.model())
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "system")
                                .put("content", "你是严格遵守JSON输出要求的A股量价分析助手。"))
                        .add(objectMapper.createObjectNode()
                                .put("role", "user")
                                .put("content", prompt)));

        HttpRequest request = HttpRequest.newBuilder(URI.create(AiEndpointResolver.resolveChatCompletionsUrl(aiProperties.baseUrl())))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + aiProperties.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " " + response.body());
        }

        String content = extractContent(objectMapper.readTree(response.body()));
        return objectMapper.readTree(extractJson(content));
    }

    private StockAiAnalysisResponse buildFallbackResponse(JsonNode stockData, String status) {
        JsonNode metrics = stockData.path("metrics");
        double change = metrics.path("periodChangePercent").asDouble(0.0);
        double volumeRatio = metrics.path("recentVolumeRatio").asDouble(1.0);
        String trendBias = change > 3 ? "偏多" : change > 0.8 ? "震荡偏多" : change < -3 ? "偏空" : change < -0.8 ? "震荡偏空" : "震荡";
        String actionBias = volumeRatio > 1.25 && change > 0 ? "突破后跟随" : change > 0 ? "逢回踩观察" : change < 0 ? "等待确认" : "谨慎回避";
        String headline = "%s %s量价学习观察".formatted(text(stockData, "stockName"), text(stockData, "timeframeLabel"));
        String summary = "当前窗口涨跌幅约 %.2f%%，最近量能比约 %.2f。更适合把它当成量价教学样本，先观察价格是否延续、回踩是否缩量，再决定是否跟随。"
                .formatted(change, volumeRatio);
        return buildResponse(
                stockData,
                headline,
                summary,
                trendBias,
                actionBias,
                status.equals("disabled") ? "低" : "中",
                defaultIfEmpty(readStringList(stockData.path("signals"), 8), List.of("当前数据可用于基础量价观察。")),
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
                firstNonBlank(text(aiRoot, "headline"), text(stockData, "stockName") + " " + text(stockData, "timeframeLabel") + " 量价分析"),
                firstNonBlank(text(aiRoot, "summary"), "AI 未返回总结。"),
                firstNonBlank(text(aiRoot, "trendBias"), "震荡"),
                firstNonBlank(text(aiRoot, "actionBias"), "等待确认"),
                firstNonBlank(text(aiRoot, "confidence"), "中"),
                readStringList(aiRoot.path("volumePriceSignals"), 8),
                readStringList(aiRoot.path("buyPoints"), 8),
                readStringList(aiRoot.path("sellPoints"), 8),
                readStringList(aiRoot.path("learningPoints"), 8),
                readStringList(aiRoot.path("riskWarnings"), 8),
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
                text(stockData, "stockCode"),
                text(stockData, "stockName"),
                text(stockData, "timeframe"),
                text(stockData, "timeframeLabel"),
                text(stockData, "source"),
                stockData.path("analyzedBars").asInt(),
                text(metrics, "windowStart"),
                text(metrics, "windowEnd"),
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
                defaultIfEmpty(volumePriceSignals, readStringList(stockData.path("signals"), 8)),
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
                    text(item, "time"),
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

    private String extractContent(JsonNode root) {
        if (root.path("choices").isArray() && !root.path("choices").isEmpty()) {
            JsonNode choice = root.path("choices").get(0);
            JsonNode content = choice.path("message").path("content");
            if (content.isTextual() && !content.asText().isBlank()) {
                return content.asText();
            }
            if (content.isArray()) {
                StringBuilder builder = new StringBuilder();
                for (JsonNode item : content) {
                    if (item.has("text")) {
                        builder.append(item.path("text").asText()).append("\n");
                    }
                }
                if (!builder.toString().isBlank()) {
                    return builder.toString();
                }
            }
        }
        throw new IllegalStateException("无法从 AI 响应中提取文本");
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

    private boolean aiEnabled() {
        return aiProperties.enabled() && !isBlank(aiProperties.apiKey()) && !isBlank(aiProperties.baseUrl());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private Double nullableDouble(JsonNode node) {
        return node.isNumber() ? node.asDouble() : null;
    }

    private List<String> readStringList(JsonNode node, int maxSize) {
        List<String> result = new ArrayList<>();
        if (!node.isArray()) {
            return result;
        }
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                result.add(value);
            }
            if (result.size() >= maxSize) {
                break;
            }
        }
        return result;
    }

    private List<String> defaultIfEmpty(List<String> preferred, List<String> fallback) {
        return preferred == null || preferred.isEmpty() ? fallback : preferred;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
