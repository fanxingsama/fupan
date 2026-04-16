package com.meirifupan.backend.service;

import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.MarketIndicators;
import com.meirifupan.backend.model.MarketIndicators.LadderLevel;
import com.meirifupan.backend.model.MarketIndicators.RiskSignal;
import com.meirifupan.backend.model.StockRecord;
import com.meirifupan.backend.model.TrendPoint;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 根据原始复盘数据计算市场情绪指标。
 */
@Service
public class IndicatorService {
    // AI-READABLE-RULE-ENGINE:
    // This class is rule-based, not model-based.
    // Emotion phase, market scores, and risk signals are computed from recap statistics.

    public int maxBoardHeight(DailyRecapReport report) {
        int max = 0;
        for (StockRecord stock : safe(report.limitUpToday())) {
            max = Math.max(max, Math.max(parseBoardHeight(stock.boardHeight()), 1));
        }
        for (StockRecord stock : safe(report.firstLimitToday())) {
            max = Math.max(max, Math.max(parseBoardHeight(stock.boardHeight()), 1));
        }
        return max;
    }

    public int limitUpTotal(DailyRecapReport report) {
        return safe(report.limitUpToday()).size() + safe(report.firstLimitToday()).size();
    }

    public int limitDownTotal(DailyRecapReport report) {
        return safe(report.limitDownToday()).size();
    }

    public int brokenCount(DailyRecapReport report) {
        return safe(report.brokenLimitToday()).size();
    }

    public Double sealRate(DailyRecapReport report) {
        int sealed = limitUpTotal(report);
        int broken = brokenCount(report);
        int total = sealed + broken;
        if (total == 0) {
            return null;
        }
        return round2((double) sealed / total * 100);
    }

    public Double limitRatio(DailyRecapReport report) {
        int up = limitUpTotal(report);
        int down = limitDownTotal(report);
        if (down == 0) {
            return up > 0 ? (double) up : null;
        }
        return round2((double) up / down);
    }

    public Double yesterdayLimitPremium(DailyRecapReport report) {
        return averageChangePercent(report.limitUpYesterdayFeedback());
    }

    public Double yesterdayLimitWinRate(DailyRecapReport report) {
        List<StockRecord> rows = safe(report.limitUpYesterdayFeedback());
        if (rows.isEmpty()) {
            return null;
        }
        int up = 0;
        for (StockRecord row : rows) {
            Double value = parsePercent(row.changePercent());
            if (value != null && value > 0) {
                up++;
            }
        }
        return round2((double) up / rows.size() * 100);
    }

    public Double yesterdayBrokenAvg(DailyRecapReport report) {
        return averageChangePercent(report.brokenLimitYesterdayFeedback());
    }

    public List<LadderLevel> boardLadder(DailyRecapReport report) {
        Map<Integer, Integer> ladder = new TreeMap<>();
        int firstCount = safe(report.firstLimitToday()).size();
        if (firstCount > 0) {
            ladder.put(1, firstCount);
        }

        for (StockRecord stock : safe(report.limitUpToday())) {
            int height = parseBoardHeight(stock.boardHeight());
            if (height >= 2) {
                ladder.merge(height, 1, Integer::sum);
            }
        }

        List<LadderLevel> result = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : ladder.entrySet()) {
            result.add(new LadderLevel(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public TrendPoint toTrendPoint(DailyRecapReport report) {
        Double sr = sealRate(report);
        Double premium = yesterdayLimitPremium(report);
        Double ratio = limitRatio(report);
        return new TrendPoint(
                report.tradeDate().toString(),
                report.marketStats().upCount(),
                maxBoardHeight(report),
                report.marketStats().firstLimitCount(),
                sr != null ? sr : 0,
                limitUpTotal(report),
                brokenCount(report),
                premium != null ? premium : 0,
                ratio != null ? ratio : 0,
                parseAmount(report.marketStats().totalTurnover()) != null ? parseAmount(report.marketStats().totalTurnover()) : 0
        );
    }

    public MarketIndicators calculate(DailyRecapReport report, List<DailyRecapReport> recentReports) {
        EmotionResult emotion = emotionPhase(recentReports);
        return new MarketIndicators(
                sealRate(report),
                limitRatio(report),
                limitUpTotal(report),
                limitDownTotal(report),
                brokenCount(report),
                yesterdayLimitPremium(report),
                yesterdayLimitWinRate(report),
                yesterdayBrokenAvg(report),
                maxBoardHeight(report),
                boardLadder(report),
                emotion.phase(),
                emotion.label(),
                emotion.color(),
                emotion.description(),
                speculationScore(report),
                continuationScore(report),
                breadthScore(report),
                riskSignals(report, recentReports)
        );
    }

    private record EmotionResult(String phase, String label, String color, String description) {}

    private EmotionResult emotionPhase(List<DailyRecapReport> recentReports) {
        if (recentReports == null || recentReports.size() < 3) {
            return new EmotionResult("unknown", "数据不足", "#94a3b8", "历史数据不足，暂时无法判断情绪周期");
        }

        List<DailyRecapReport> recent = recentReports.size() > 5
                ? recentReports.subList(recentReports.size() - 5, recentReports.size())
                : recentReports;

        List<Double> sealRates = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        List<Integer> limitTotals = new ArrayList<>();

        for (DailyRecapReport report : recent) {
            Double sealRate = sealRate(report);
            if (sealRate != null) {
                sealRates.add(sealRate);
            }
            heights.add(maxBoardHeight(report));
            limitTotals.add(limitUpTotal(report));
        }

        double lastSeal = sealRates.isEmpty() ? 60 : sealRates.get(sealRates.size() - 1);
        int lastHeight = heights.get(heights.size() - 1);
        int lastTotal = limitTotals.get(limitTotals.size() - 1);
        int prevHeight = heights.size() >= 2 ? heights.get(heights.size() - 2) : lastHeight;
        double avgSeal = sealRates.isEmpty() ? 60 : sealRates.stream().mapToDouble(Double::doubleValue).average().orElse(60);

        int heightTrend = lastHeight - prevHeight;
        boolean heightRising = heights.size() >= 3 && heights.get(heights.size() - 1) > heights.get(heights.size() - 3);
        boolean heightFalling = heights.size() >= 3 && heights.get(heights.size() - 1) < heights.get(heights.size() - 3);
        boolean sealHigh = lastSeal >= 75;
        boolean sealLow = lastSeal < 55;

        if (sealLow && lastTotal < 30 && lastHeight <= 3) {
            return new EmotionResult("ice", "冰点期", "#64748b", "涨停少、封板率弱，等待确定性机会比硬做更重要");
        }
        if (heightFalling && sealLow) {
            return new EmotionResult("retreat", "退潮期", "#0d9488", "高标承接转弱，追涨性价比低，控制回撤优先");
        }
        if (heightTrend < 0 && lastHeight >= 3) {
            return new EmotionResult("diverge", "分歧期", "#f59e0b", "高标分歧开始放大，只做最前排的强势票");
        }
        if (sealHigh && lastTotal >= 50 && lastHeight >= 5) {
            return new EmotionResult("climax", "高潮期", "#ef4444", "市场热度很高，但一致性过强时不适合盲目追后排");
        }
        if (heightRising && lastSeal >= 60) {
            return new EmotionResult("ferment", "发酵期", "#f97316", "主线高度在抬升，适合围绕核心题材做跟随");
        }
        if (heightTrend >= 0 && lastTotal >= 25 && avgSeal >= 55) {
            return new EmotionResult("repair", "修复期", "#22c55e", "情绪从低位回升，可优先试错低位核心首板");
        }
        if (lastSeal >= 60) {
            return new EmotionResult("ferment", "发酵期", "#f97316", "情绪有所升温，但仍需确认主线持续性");
        }
        return new EmotionResult("retreat", "退潮期", "#0d9488", "赚钱效应不强，以观察和防守为主");
    }

    private List<RiskSignal> riskSignals(DailyRecapReport report, List<DailyRecapReport> recentReports) {
        List<RiskSignal> signals = new ArrayList<>();

        Double sealRate = sealRate(report);
        if (sealRate != null) {
            if (sealRate < 50) {
                signals.add(new RiskSignal("danger", "封板率仅 " + sealRate + "%，追高容易吃面"));
            } else if (sealRate < 65) {
                signals.add(new RiskSignal("warning", "封板率 " + sealRate + "%，分歧较大，重在辨别前排"));
            } else if (sealRate >= 80) {
                signals.add(new RiskSignal("safe", "封板率 " + sealRate + "%，情绪一致性较强"));
            }
        }

        int total = limitUpTotal(report);
        if (total < 20) {
            signals.add(new RiskSignal("danger", "涨停家数只有 " + total + "，交易机会明显偏少"));
        } else if (total < 35) {
            signals.add(new RiskSignal("warning", "涨停家数 " + total + "，适合精选少数确定性标的"));
        } else if (total >= 60) {
            signals.add(new RiskSignal("safe", "涨停家数 " + total + "，市场有较强进攻氛围"));
        }

        Double premium = yesterdayLimitPremium(report);
        if (premium != null) {
            if (premium < -2) {
                signals.add(new RiskSignal("danger", "昨日涨停溢价 " + premium + "%，隔日亏钱效应明显"));
            } else if (premium < 0) {
                signals.add(new RiskSignal("warning", "昨日涨停溢价 " + premium + "%，接力需要更谨慎"));
            } else if (premium >= 2) {
                signals.add(new RiskSignal("safe", "昨日涨停溢价 +" + premium + "%，短线承接不错"));
            }
        }

        Double ratio = limitRatio(report);
        if (ratio != null) {
            if (ratio < 2) {
                signals.add(new RiskSignal("danger", "涨跌停比 " + ratio + ":1，多空失衡，切忌乱追"));
            } else if (ratio >= 5) {
                signals.add(new RiskSignal("safe", "涨跌停比 " + ratio + ":1，多方优势明显"));
            }
        }

        if (recentReports != null && recentReports.size() >= 2) {
            DailyRecapReport prev = recentReports.get(recentReports.size() - 2);
            DailyRecapReport curr = recentReports.get(recentReports.size() - 1);
            int prevMax = maxBoardHeight(prev);
            int currMax = maxBoardHeight(curr);
            if (prevMax - currMax >= 2 && prevMax >= 4) {
                signals.add(new RiskSignal("danger", "最高板从 " + prevMax + " 板降到 " + currMax + " 板，高标断层明显"));
            }
        }

        return signals;
    }

    private double speculationScore(DailyRecapReport report) {
        double score = 0;
        score += scaled(sealRate(report), 0, 100, 35);
        score += scaled((double) limitUpTotal(report), 0, 80, 30);
        score += scaled((double) maxBoardHeight(report), 0, 8, 20);
        score += scaled((double) Math.max(0, 20 - brokenCount(report)), 0, 20, 15);
        return round2(score);
    }

    private double continuationScore(DailyRecapReport report) {
        double score = 0;
        score += scaled(yesterdayLimitPremium(report), -5, 6, 45);
        score += scaled(yesterdayLimitWinRate(report), 0, 100, 35);
        score += scaled(yesterdayBrokenAvg(report), -6, 6, 20);
        return round2(score);
    }

    private double breadthScore(DailyRecapReport report) {
        int up = report.marketStats() != null ? report.marketStats().upCount() : 0;
        int down = report.marketStats() != null ? report.marketStats().downCount() : 0;
        int total = Math.max(up + down, 1);
        return round2((double) up / total * 100);
    }

    private Double averageChangePercent(List<StockRecord> rows) {
        List<StockRecord> safeRows = safe(rows);
        if (safeRows.isEmpty()) {
            return null;
        }

        double sum = 0;
        int count = 0;
        for (StockRecord row : safeRows) {
            Double value = parsePercent(row.changePercent());
            if (value != null) {
                sum += value;
                count++;
            }
        }
        return count > 0 ? round2(sum / count) : null;
    }

    private static double scaled(Double value, double min, double max, double weight) {
        if (value == null) {
            return weight * 0.5;
        }
        double normalized = (value - min) / (max - min);
        normalized = Math.max(0, Math.min(1, normalized));
        return normalized * weight;
    }

    private static Double parsePercent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().replace("%", "");
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Double parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.endsWith("亿")) {
                return Double.parseDouble(text.substring(0, text.length() - 1)) * 100_000_000;
            }
            if (text.endsWith("万")) {
                return Double.parseDouble(text.substring(0, text.length() - 1)) * 10_000;
            }
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int parseBoardHeight(String value) {
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
}
