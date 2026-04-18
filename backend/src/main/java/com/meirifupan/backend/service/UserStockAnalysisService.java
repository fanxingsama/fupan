package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meirifupan.backend.config.AiProperties;
import com.meirifupan.backend.model.MarketIntelligence;
import com.meirifupan.backend.model.UserStockAnalysisResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UserStockAnalysisService {

    private static final Pattern DATE_PREFIX = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})_(.+)$");
    private static final String DEFAULT_DISCLAIMER = "AI 输出仅用于复盘与交易研究辅助，不构成投资建议。买卖点与动机均为基于截图、量价环境和消息面的高概率推断，仍需结合盘面自行判断。";
    private static final String CACHE_SCENARIO = "user-stock-analysis:v1";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final AiGatewayClient aiGatewayClient;
    private final MarketIntelligenceService marketIntelligenceService;
    private final AiRequestCacheService aiRequestCacheService;

    public UserStockAnalysisService(AiProperties properties,
                                    ObjectMapper objectMapper,
                                    MarketIntelligenceService marketIntelligenceService,
                                    AiGatewayClient aiGatewayClient,
                                    AiRequestCacheService aiRequestCacheService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.marketIntelligenceService = marketIntelligenceService;
        this.aiGatewayClient = aiGatewayClient;
        this.aiRequestCacheService = aiRequestCacheService;
    }

    public UserStockAnalysisResponse analyze(List<MultipartFile> files) {
        if (!aiGatewayClient.isConfigured()) {
            return new UserStockAnalysisResponse(
                    false,
                    properties.provider(),
                    properties.model(),
                    "disabled",
                    files == null ? 0 : files.size(),
                    0,
                    "",
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of("AI 未启用，请先配置 AI_ENABLED、AI_API_KEY、AI_BASE_URL、AI_MODEL。"),
                    List.of(),
                    "AI 未启用。",
                    null
            );
        }

        List<UploadedDayImage> selectedImages = selectFirstImagePerDay(files);
        if (selectedImages.isEmpty()) {
            throw new IllegalArgumentException("没有识别到有效图片。请上传带有日期前缀的图片，例如 2026-03-06_yesterday_stock_img_xxx.png");
        }

        try {
            String cacheKey = aiRequestCacheService.buildCacheKey(CACHE_SCENARIO, buildCachePayload(selectedImages));
            var cached = aiRequestCacheService.load(cacheKey, UserStockAnalysisResponse.class);
            if (cached.isPresent()) {
                return cached.get();
            }
            UserStockAnalysisResponse response = requestAnalysis(files.size(), selectedImages);
            aiRequestCacheService.save(cacheKey, CACHE_SCENARIO, response);
            return response;
        } catch (Exception ex) {
            return new UserStockAnalysisResponse(
                    true,
                    properties.provider(),
                    properties.model(),
                    "error",
                    files.size(),
                    selectedImages.size(),
                    "",
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of("分析失败：" + ex.getMessage()),
                    List.of(),
                    "AI 分析失败，请检查模型是否支持图片输入，或稍后重试。",
                    OffsetDateTime.now()
            );
        }
    }

    private UserStockAnalysisResponse requestAnalysis(int uploadedCount, List<UploadedDayImage> selectedImages)
            throws IOException, InterruptedException {
        JsonNode requestBody = buildRequestBody(selectedImages);

        String content = aiGatewayClient.chatCompletion(requestBody);
        JsonNode root = objectMapper.readTree(AiJsonSupport.extractJsonObject(content));
        return parseResponse(uploadedCount, selectedImages.size(), root);
    }

    private String buildCachePayload(List<UploadedDayImage> selectedImages) throws IOException {
        return objectMapper.writeValueAsString(selectedImages);
    }

    private JsonNode buildRequestBody(List<UploadedDayImage> selectedImages) throws IOException {
        var messages = objectMapper.createArrayNode();
        messages.add(objectMapper.createObjectNode()
                .put("role", "system")
                .put("content", """
                        你是A股短线交易行为分析助手。
                        你的任务不是推荐股票，而是根据用户上传的历史持仓截图，反推该交易者可能的买入原因、卖出原因、交易风格、常用买卖点和风险控制方式。
                        你可以结合截图中的盈亏、成本/现价、持仓变化，以及提供的当日市场消息面、题材、热点股和情绪背景进行推断。
                        必须明确说明这是“高概率推断”，不是对真实下单时点的确定性还原。
                        只输出 JSON，不要输出 markdown 代码块，不要额外解释。
                        """));

        var userContent = objectMapper.createArrayNode();
        userContent.add(objectMapper.createObjectNode()
                .put("type", "text")
                .put("text", buildInstructionPrompt(selectedImages)));

        for (UploadedDayImage image : selectedImages) {
            userContent.add(objectMapper.createObjectNode()
                    .put("type", "text")
                    .put("text", buildDayContextText(image)));
            userContent.add(objectMapper.createObjectNode()
                    .put("type", "image_url")
                    .set("image_url", objectMapper.createObjectNode()
                            .put("url", image.dataUrl())));
        }

        messages.add(objectMapper.createObjectNode()
                .put("role", "user")
                .set("content", userContent));

        return aiGatewayClient.newChatRequest(messages);
    }

    private String buildInstructionPrompt(List<UploadedDayImage> selectedImages) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                下面会提供同一个交易者多天的持仓截图。文件名里的日期就是对应交易日。
                规则：
                1. 同一天如果原始上传里有多张图片，系统已经默认只保留该日第一张。
                2. 你需要结合截图内容与当天市场背景，分析此人可能的买卖原因、偏好的模式和风格。
                3. 允许推断，但请把结论写成“可能/更像/高概率”。
                4. 买卖点要尽量具体到分时类型，例如：竞价超预期、分歧低吸、回封确认、均线承接、尾盘取关、冲高兑现等。
                5. 对每个交易日，尽量从截图里提取股票名称、盈亏、成本/现价、持仓变化，再反推动机。
                6. 最终输出 JSON，格式如下：
                {
                  "overallConclusion": "整体结论",
                  "tradingStyleProfile": "对这个人的短线风格画像",
                  "styleTags": ["风格标签"],
                  "recurringPatterns": ["重复出现的交易模式"],
                  "methodology": ["你总结出的可学习方法论"],
                  "riskWarnings": ["推断边界与风险提示"],
                  "dayAnalyses": [
                    {
                      "tradeDate": "2026-03-06",
                      "imageName": "文件名",
                      "imageType": "today_stock_img 或 yesterday_stock_img",
                      "summary": "当日概括",
                      "holdings": ["从截图读出的主要股票与盈亏信息"],
                      "inferredReasons": ["可能的买入/卖出原因"],
                      "probableBuyPoints": ["可能的买点类型或价格区间推断"],
                      "probableSellPoints": ["可能的卖点类型或价格区间推断"],
                      "volumePriceClues": ["量价结构、分时、位置推断"],
                      "newsDrivers": ["可能的题材/消息/板块催化"],
                      "nextDayFocus": ["如果隔日继续跟踪，重点看什么"]
                    }
                  ]
                }
                """);

        builder.append("\n本次共分析 ").append(selectedImages.size()).append(" 个交易日。\n");
        return builder.toString();
    }

    private String buildDayContextText(UploadedDayImage image) {
        StringBuilder builder = new StringBuilder();
        builder.append("交易日：").append(image.tradeDate()).append("\n");
        builder.append("文件名：").append(image.originalFilename()).append("\n");
        builder.append("截图类型：").append(image.imageType()).append("\n");
        builder.append("市场背景摘要：\n").append(image.marketContext()).append("\n");
        return builder.toString();
    }

    private UserStockAnalysisResponse parseResponse(int uploadedCount, int analyzedDayCount, JsonNode root) {
        List<String> styleTags = AiJsonSupport.readStringList(root.path("styleTags"), 8);
        List<String> patterns = AiJsonSupport.readStringList(root.path("recurringPatterns"), 8);
        List<String> methodology = AiJsonSupport.readStringList(root.path("methodology"), 8);
        List<String> risks = AiJsonSupport.readStringList(root.path("riskWarnings"), 8);

        List<UserStockAnalysisResponse.DayAnalysis> dayAnalyses = new ArrayList<>();
        if (root.path("dayAnalyses").isArray()) {
            for (JsonNode item : root.path("dayAnalyses")) {
                dayAnalyses.add(new UserStockAnalysisResponse.DayAnalysis(
                        AiJsonSupport.text(item, "tradeDate"),
                        AiJsonSupport.text(item, "imageName"),
                        AiJsonSupport.text(item, "imageType"),
                        AiJsonSupport.text(item, "summary"),
                        AiJsonSupport.readStringList(item.path("holdings"), 12),
                        AiJsonSupport.readStringList(item.path("inferredReasons"), 12),
                        AiJsonSupport.readStringList(item.path("probableBuyPoints"), 8),
                        AiJsonSupport.readStringList(item.path("probableSellPoints"), 8),
                        AiJsonSupport.readStringList(item.path("volumePriceClues"), 8),
                        AiJsonSupport.readStringList(item.path("newsDrivers"), 8),
                        AiJsonSupport.readStringList(item.path("nextDayFocus"), 8)
                ));
            }
        }

        return new UserStockAnalysisResponse(
                true,
                properties.provider(),
                properties.model(),
                "ready",
                uploadedCount,
                analyzedDayCount,
                AiJsonSupport.text(root, "overallConclusion"),
                AiJsonSupport.text(root, "tradingStyleProfile"),
                styleTags,
                patterns,
                methodology,
                risks,
                dayAnalyses,
                DEFAULT_DISCLAIMER,
                OffsetDateTime.now()
        );
    }

    private List<UploadedDayImage> selectFirstImagePerDay(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        Map<LocalDate, MultipartFile> firstByDate = new HashMap<>();
        Map<LocalDate, String> firstNameByDate = new HashMap<>();

        files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .sorted(Comparator.comparing(file -> safeFilename(file.getOriginalFilename())))
                .forEach(file -> {
                    ParsedFilename parsed = parseFilename(file.getOriginalFilename());
                    if (parsed == null) return;
                    firstByDate.putIfAbsent(parsed.tradeDate(), file);
                    firstNameByDate.putIfAbsent(parsed.tradeDate(), safeFilename(file.getOriginalFilename()));
                });

        List<UploadedDayImage> result = new ArrayList<>();
        firstByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    MultipartFile file = entry.getValue();
                    ParsedFilename parsed = parseFilename(firstNameByDate.get(entry.getKey()));
                    if (parsed == null) return;
                    try {
                        String dataUrl = toCompressedDataUrl(file);
                        String marketContext = loadMarketContext(parsed.tradeDate());
                        result.add(new UploadedDayImage(
                                parsed.tradeDate(),
                                safeFilename(file.getOriginalFilename()),
                                parsed.imageType(),
                                dataUrl,
                                marketContext
                        ));
                    } catch (Exception ignored) {
                    }
                });

        return result;
    }

    private String loadMarketContext(LocalDate tradeDate) {
        try {
            MarketIntelligence intelligence = marketIntelligenceService.loadOrCollect(tradeDate, false);
            List<String> chunks = new ArrayList<>();

            intelligence.themeClusters().stream().limit(3).forEach(theme ->
                    chunks.add("主题：" + theme.name() + "；热度=" + theme.heat() + "；相关股=" + String.join("、", safeList(theme.relatedStocks()).stream().limit(4).toList())));

            intelligence.hotStocks().stream().limit(5).forEach(stock ->
                    chunks.add("热股：" + stock.name() + "(" + stock.code() + ") " + stock.changePercent() + "；关键词=" + String.join("、", safeList(stock.keywords()).stream().limit(3).toList())));

            intelligence.marketNews().stream().limit(3).forEach(news ->
                    chunks.add("市场新闻：" + news.title()));

            intelligence.stockNews().stream().limit(3).forEach(news ->
                    chunks.add("个股新闻：" + news.relatedName() + " - " + news.title()));

            if (chunks.isEmpty()) {
                return "暂无可用的市场情报摘要。";
            }
            return String.join("\n", chunks);
        } catch (Exception ex) {
            return "市场情报获取失败：" + ex.getMessage();
        }
    }

    private String toCompressedDataUrl(MultipartFile file) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
        if (source == null) {
            throw new IllegalArgumentException("无法读取图片：" + safeFilename(file.getOriginalFilename()));
        }

        BufferedImage resized = resize(source, 900);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.72f);
        }

        writer.setOutput(new MemoryCacheImageOutputStream(output));
        writer.write(null, new IIOImage(resized, null, null), param);
        writer.dispose();

        return "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private BufferedImage resize(BufferedImage source, int maxSide) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxSide) {
            if (source.getType() == BufferedImage.TYPE_INT_RGB) {
                return source;
            }
            BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = copy.createGraphics();
            graphics.drawImage(source, 0, 0, null);
            graphics.dispose();
            return copy;
        }

        double scale = maxSide / (double) longest;
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, newWidth, newHeight, null);
        graphics.dispose();
        return resized;
    }

    private ParsedFilename parseFilename(String filename) {
        String safe = safeFilename(filename);
        Matcher matcher = DATE_PREFIX.matcher(safe);
        if (!matcher.matches()) {
            return null;
        }
        LocalDate tradeDate = LocalDate.parse(matcher.group(1));
        String suffix = matcher.group(2).toLowerCase(Locale.ROOT);
        String imageType = suffix.contains("today_stock_img") ? "today_stock_img"
                : suffix.contains("yesterday_stock_img") ? "yesterday_stock_img"
                : "unknown";
        return new ParsedFilename(tradeDate, imageType);
    }

    private String safeFilename(String filename) {
        return filename == null ? "" : filename;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private record ParsedFilename(LocalDate tradeDate, String imageType) {}

    private record UploadedDayImage(
            LocalDate tradeDate,
            String originalFilename,
            String imageType,
            String dataUrl,
            String marketContext
    ) {}
}
