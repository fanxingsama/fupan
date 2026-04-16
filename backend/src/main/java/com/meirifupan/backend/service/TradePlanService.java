package com.meirifupan.backend.service;

import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.MarketIndicators;
import com.meirifupan.backend.model.SectorRecord;
import com.meirifupan.backend.model.StockRecord;
import com.meirifupan.backend.model.TradePlan;
import com.meirifupan.backend.model.TradePlan.CandidatePool;
import com.meirifupan.backend.model.TradePlan.PlanStep;
import com.meirifupan.backend.model.TradePlan.ThemeScore;
import com.meirifupan.backend.model.TradePlan.WatchStock;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Build a next-day trading plan from recap data.
 */
@Service
public class TradePlanService {
    // AI-READABLE-RULE-ENGINE:
    // This service converts recap + indicators into a next-day plan.
    // Theme scores and watch-stock scores are fully rule-based at the moment.

    public TradePlan buildPlan(DailyRecapReport report, MarketIndicators indicators) {
        List<ThemeScore> themes = buildThemes(report);
        List<WatchStock> watchStocks = buildWatchStocks(report, themes);
        String tradeMode = determineTradeMode(indicators);

        return new TradePlan(
                buildHeadline(indicators, themes, tradeMode),
                determineMarketBias(indicators),
                tradeMode,
                determinePositionAdvice(indicators),
                buildExecutionSummary(themes, tradeMode),
                buildNextDayFocus(indicators, themes, watchStocks),
                buildRiskFocus(indicators),
                themes,
                buildPools(themes, watchStocks),
                watchStocks.stream().limit(6).toList(),
                buildSchedule(themes, tradeMode)
        );
    }

    private List<ThemeScore> buildThemes(DailyRecapReport report) {
        Map<String, ThemeAccumulator> stats = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry : safeMap(report.firstLimitSectorFocus()).entrySet()) {
            String name = normalizeTheme(entry.getKey());
            if (name != null) {
                stats.computeIfAbsent(name, ignored -> new ThemeAccumulator()).firstLimitCount += Math.max(entry.getValue(), 0);
            }
        }

        for (SectorRecord sector : safe(report.topUpSectors())) {
            String name = normalizeTheme(sector.name());
            if (name != null) {
                stats.computeIfAbsent(name, ignored -> new ThemeAccumulator()).sectorHeat += Math.max(0, parsePercent(sector.changePercent()));
            }
        }

        List<ThemeScore> result = new ArrayList<>();
        for (Map.Entry<String, ThemeAccumulator> entry : stats.entrySet()) {
            ThemeAccumulator value = entry.getValue();
            double score = Math.min(100, value.firstLimitCount * 12 + value.sectorHeat * 8);
            String phase = score >= 75 ? "主升" : score >= 50 ? "发酵" : "轮动";
            String comment = value.firstLimitCount >= 3
                    ? "首板扩散较明显，优先盯最前排的承接与卡位。"
                    : "更多是轮动试错，避免追后排一致性。";
            result.add(new ThemeScore(entry.getKey(), round2(score), phase, comment));
        }

        result.sort(Comparator.comparingDouble(ThemeScore::score).reversed());
        return result.stream().limit(6).toList();
    }

    private List<WatchStock> buildWatchStocks(DailyRecapReport report, List<ThemeScore> themes) {
        Map<String, Double> themeScores = new LinkedHashMap<>();
        for (ThemeScore theme : themes) {
            themeScores.put(theme.name(), theme.score());
        }

        List<WatchStock> result = new ArrayList<>();
        for (StockRecord stock : safe(report.limitUpToday())) {
            result.add(toWatchStock(stock, "连板接力", themeScores));
        }
        for (StockRecord stock : safe(report.firstLimitToday())) {
            result.add(toWatchStock(stock, "首板试错", themeScores));
        }
        for (StockRecord stock : safe(report.brokenLimitToday())) {
            result.add(toWatchStock(stock, "回封修复", themeScores));
        }

        result.sort(Comparator.comparingDouble(WatchStock::score).reversed());
        return result;
    }

    private WatchStock toWatchStock(StockRecord stock, String sourceRole, Map<String, Double> themeScores) {
        String theme = pickTheme(stock);
        int boardHeight = parseInt(stock.boardHeight());
        double themeScore = themeScores.getOrDefault(theme, 35.0);
        double sealScore = scoreSealTime(stock.extraTag());
        double turnoverScore = scoreTurnover(stock.turnoverRate());
        double heightScore = Math.min(25, Math.max(boardHeight, 1) * 6.0);
        double amountScore = scoreSealAmount(stock.sealAmount());
        double totalScore = round2(themeScore * 0.35 + sealScore * 0.2 + turnoverScore * 0.15 + heightScore * 0.2 + amountScore * 0.1);

        String role = boardHeight >= 4 ? "龙头观察" : boardHeight >= 2 ? "前排接力" : "首板试错";
        if ("回封修复".equals(sourceRole)) {
            role = "回封修复";
        }

        String summary = theme + " | " + role + " | " + buildSealSummary(stock.extraTag());
        String planA = boardHeight >= 2
                ? "竞价不低于预期且题材有助攻时，只考虑分歧后的承接机会。"
                : "优先等开盘后回踩承接，不做后排直线拉升追价。";
        String planB = "若竞价一般但题材继续发酵，可等换手充分后的二次确认。";
        if ("回封修复".equals(role)) {
            planB = "仅在快速回封且同题材回流时跟随，回封失败直接放弃。";
        }
        String risk = boardHeight >= 4
                ? "高标波动大，仓位必须受控，炸板时不能恋战。"
                : "若板块没有扩散或竞价低于预期，宁可放弃。";

        return new WatchStock(
                stock.code(),
                stock.name(),
                role,
                theme,
                totalScore,
                summary,
                planA,
                planB,
                risk
        );
    }

    private List<CandidatePool> buildPools(List<ThemeScore> themes, List<WatchStock> watchStocks) {
        Set<String> strongThemes = themes.stream().map(ThemeScore::name).limit(3).collect(java.util.stream.Collectors.toSet());

        List<WatchStock> leaders = watchStocks.stream()
                .filter(stock -> stock.role().contains("龙头") || stock.role().contains("前排"))
                .limit(5)
                .toList();
        List<WatchStock> firstLimits = watchStocks.stream()
                .filter(stock -> stock.role().contains("首板"))
                .limit(5)
                .toList();
        List<WatchStock> repairs = watchStocks.stream()
                .filter(stock -> stock.role().contains("回封"))
                .limit(4)
                .toList();
        List<WatchStock> themeExt = watchStocks.stream()
                .filter(stock -> strongThemes.contains(stock.theme()))
                .limit(5)
                .toList();

        return List.of(
                new CandidatePool("leaders", "核心龙头池", "只保留最前排，解决“明天盯谁”这个问题。", leaders),
                new CandidatePool("firstLimit", "首板试错池", "更适合修复期与高低切，优先看早盘强承接。", firstLimits),
                new CandidatePool("repair", "回封修复池", "只在分歧转一致时参与，避免退潮日硬接。", repairs),
                new CandidatePool("theme", "主线扩散池", "从最强题材里找补涨和中位辨识度标的。", themeExt)
        );
    }

    private List<String> buildNextDayFocus(MarketIndicators indicators, List<ThemeScore> themes, List<WatchStock> watchStocks) {
        List<String> focus = new ArrayList<>();
        if (!themes.isEmpty()) {
            focus.add("优先确认 " + themes.get(0).name() + " 是否继续得到竞价与首板助攻。");
        }
        if (indicators.maxBoardHeight() >= 4) {
            focus.add("先确认高标能否继续打开高度，再决定是否做接力。");
        } else {
            focus.add("高标高度一般，更适合从低位首板与弱转强里找机会。");
        }
        if (!watchStocks.isEmpty()) {
            WatchStock first = watchStocks.get(0);
            focus.add("重点盯 " + first.name() + " 的竞价强弱，它能反映短线资金进攻方向。");
        }
        focus.add(indicators.yesterdayLimitPremium() != null && indicators.yesterdayLimitPremium() > 0
                ? "隔日溢价尚可，可以保留部分接力预期。"
                : "隔日溢价一般，尽量避免盲目追一致性后排。");
        return focus;
    }

    private List<String> buildRiskFocus(MarketIndicators indicators) {
        List<String> risks = new ArrayList<>();
        if (indicators.sealRate() != null && indicators.sealRate() < 65) {
            risks.add("封板率不高，板上接力必须严格只做前排。");
        }
        if (indicators.yesterdayLimitPremium() != null && indicators.yesterdayLimitPremium() < 0) {
            risks.add("昨日涨停溢价为负，竞价不及预期的票尽量不接。");
        }
        if (indicators.maxBoardHeight() >= 5) {
            risks.add("高标已经较高，任何一致性过强的加速都要提防次日大分歧。");
        }
        if (indicators.brokenCount() >= indicators.limitUpTotal()) {
            risks.add("炸板数偏多，说明分歧强，回封失败的票不要反复纠缠。");
        }
        if (risks.isEmpty()) {
            risks.add("主要风险来自追后排和盘中冲动，按预案执行即可。");
        }
        return risks;
    }

    private List<PlanStep> buildSchedule(List<ThemeScore> themes, String tradeMode) {
        String theme = themes.isEmpty() ? "主线方向" : themes.get(0).name();
        return List.of(
                new PlanStep("09:20-09:30", "竞价定方向", "看 " + theme + " 的核心票是否高开有量，再决定是否按“" + tradeMode + "”执行。"),
                new PlanStep("09:30-10:00", "只盯最强前排", "前30分钟重点看龙头承接、同题材助攻和弱转强。"),
                new PlanStep("10:00-11:00", "确认扩散还是回落", "主线扩散就围绕前排观察，否则减少出手次数。"),
                new PlanStep("14:00-14:50", "尾盘做取舍", "只保留次日还有预期的核心仓位，尾盘被动回封的票谨慎留仓。")
        );
    }

    private String determineTradeMode(MarketIndicators indicators) {
        return switch (indicators.emotionPhase()) {
            case "ice", "retreat" -> "空仓等待 / 低位试错";
            case "repair" -> "低位首板 + 弱转强";
            case "ferment" -> "主线前排接力";
            case "climax" -> "去弱留强，不追后排";
            default -> "只做辨识度核心";
        };
    }

    private String determineMarketBias(MarketIndicators indicators) {
        if (indicators.speculationScore() >= 70 && indicators.continuationScore() >= 60) {
            return "进攻优先";
        }
        if (indicators.speculationScore() >= 55) {
            return "均衡偏进攻";
        }
        return "防守优先";
    }

    private String determinePositionAdvice(MarketIndicators indicators) {
        return switch (indicators.emotionPhase()) {
            case "climax" -> "5成以内，只留核心仓位";
            case "ferment" -> "5-7成，聚焦主线前排";
            case "repair" -> "3-5成，以试错仓为主";
            default -> "0-3成，控制回撤优先";
        };
    }

    private String buildHeadline(MarketIndicators indicators, List<ThemeScore> themes, String tradeMode) {
        String theme = themes.isEmpty() ? "主线待确认" : themes.get(0).name();
        return indicators.emotionLabel() + "，围绕 " + theme + " 执行 " + tradeMode;
    }

    private String buildExecutionSummary(List<ThemeScore> themes, String tradeMode) {
        String theme = themes.isEmpty() ? "强势方向" : themes.get(0).name();
        return "当前更适合按“" + tradeMode + "”的思路做交易，先确认 " + theme + " 是否继续获得资金认可，再决定是否提高仓位。";
    }

    private String normalizeTheme(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        return text.isEmpty() ? null : text;
    }

    private String pickTheme(StockRecord stock) {
        if (stock.concept() != null && !stock.concept().isBlank()) {
            return stock.concept();
        }
        if (stock.industry() != null && !stock.industry().isBlank()) {
            return stock.industry();
        }
        return "未归类";
    }

    private String buildSealSummary(String extraTag) {
        if (extraTag == null || extraTag.isBlank()) {
            return "封板时间待确认";
        }
        return "封板 " + extraTag;
    }

    private double scoreSealTime(String value) {
        if (value == null || value.isBlank()) {
            return 45;
        }
        String digits = value.replace(":", "");
        try {
            int time = Integer.parseInt(digits);
            if (time <= 93500) {
                return 95;
            }
            if (time <= 100000) {
                return 80;
            }
            if (time <= 110000) {
                return 68;
            }
            if (time <= 143000) {
                return 55;
            }
            return 40;
        } catch (NumberFormatException ex) {
            return 45;
        }
    }

    private double scoreTurnover(String turnoverRate) {
        Double value = parsePercent(turnoverRate);
        if (value == null) {
            return 50;
        }
        if (value >= 8 && value <= 25) {
            return 90;
        }
        if (value >= 4 && value < 8) {
            return 72;
        }
        if (value > 25) {
            return 55;
        }
        return 45;
    }

    private double scoreSealAmount(String sealAmount) {
        Double value = IndicatorService.parseAmount(sealAmount);
        if (value == null) {
            return 50;
        }
        if (value >= 500_000_000) {
            return 90;
        }
        if (value >= 200_000_000) {
            return 75;
        }
        if (value >= 80_000_000) {
            return 60;
        }
        return 40;
    }

    private static double parsePercent(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value.replace("%", "").trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static <T> List<T> safe(List<T> list) {
        return list != null ? list : List.of();
    }

    private static <K, V> Map<K, V> safeMap(Map<K, V> map) {
        return map != null ? map : Map.of();
    }

    private static final class ThemeAccumulator {
        private int firstLimitCount;
        private double sectorHeat;
    }
}
