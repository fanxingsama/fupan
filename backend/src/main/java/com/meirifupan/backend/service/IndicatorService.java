package com.meirifupan.backend.service;

import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.MarketIndicators;
import com.meirifupan.backend.model.MarketIndicators.LadderLevel;
import com.meirifupan.backend.model.MarketIndicators.RiskSignal;
import com.meirifupan.backend.model.StockRecord;
import com.meirifupan.backend.model.TrendPoint;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 指标计算服务 —— 根据 DailyRecapReport 原始数据计算短线交易核心指标。
 * <p>
 * 所有计算逻辑集中在后端，前端仅负责展示。
 */
@Service
public class IndicatorService {

    // ── 数值解析工具 ──

    private static Double parsePercent(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.endsWith("%")) text = text.substring(0, text.length() - 1);
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseBoardHeight(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ── 单日指标计算 ──

    /** 最高连板高度 */
    public int maxBoardHeight(DailyRecapReport report) {
        int max = 0;
        for (StockRecord s : safe(report.limitUpToday())) {
            max = Math.max(max, Math.max(parseBoardHeight(s.boardHeight()), 1));
        }
        for (StockRecord s : safe(report.firstLimitToday())) {
            max = Math.max(max, Math.max(parseBoardHeight(s.boardHeight()), 1));
        }
        return max;
    }

    /** 涨停总数（连板 + 首板） */
    public int limitUpTotal(DailyRecapReport report) {
        return safe(report.limitUpToday()).size() + safe(report.firstLimitToday()).size();
    }

    /** 跌停总数 */
    public int limitDownTotal(DailyRecapReport report) {
        return safe(report.limitDownToday()).size();
    }

    /** 炸板总数 */
    public int brokenCount(DailyRecapReport report) {
        return safe(report.brokenLimitToday()).size();
    }

    /** 封板率(%) */
    public Double sealRate(DailyRecapReport report) {
        int sealed = limitUpTotal(report);
        int broken = brokenCount(report);
        int total = sealed + broken;
        if (total == 0) return null;
        return Math.round((double) sealed / total * 10000.0) / 100.0;
    }

    /** 涨跌停比 */
    public Double limitRatio(DailyRecapReport report) {
        int up = limitUpTotal(report);
        int down = limitDownTotal(report);
        if (down == 0) return up > 0 ? (double) up : null;
        return Math.round((double) up / down * 100.0) / 100.0;
    }

    /** 昨日涨停今日平均涨幅(%) */
    public Double yesterdayLimitPremium(DailyRecapReport report) {
        return averageChangePercent(report.limitUpYesterdayFeedback());
    }

    /** 昨日涨停今日上涨比例(%) */
    public Double yesterdayLimitWinRate(DailyRecapReport report) {
        List<StockRecord> rows = safe(report.limitUpYesterdayFeedback());
        if (rows.isEmpty()) return null;
        int up = 0;
        for (StockRecord r : rows) {
            Double v = parsePercent(r.changePercent());
            if (v != null && v > 0) up++;
        }
        return Math.round((double) up / rows.size() * 10000.0) / 100.0;
    }

    /** 昨日炸板今日平均涨幅(%) */
    public Double yesterdayBrokenAvg(DailyRecapReport report) {
        return averageChangePercent(report.brokenLimitYesterdayFeedback());
    }

    /** 连板梯队 */
    public List<LadderLevel> boardLadder(DailyRecapReport report) {
        Map<Integer, Integer> map = new TreeMap<>();
        int firstCount = safe(report.firstLimitToday()).size();
        if (firstCount > 0) map.put(1, firstCount);

        for (StockRecord s : safe(report.limitUpToday())) {
            int h = parseBoardHeight(s.boardHeight());
            if (h >= 2) map.merge(h, 1, Integer::sum);
        }
        List<LadderLevel> result = new ArrayList<>();
        for (var entry : map.entrySet()) {
            result.add(new LadderLevel(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    // ── 趋势数据点 ──

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
                ratio != null ? ratio : 0
        );
    }

    // ── 完整指标组 ──

    public MarketIndicators calculate(DailyRecapReport report, List<DailyRecapReport> recentReports) {
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
                emotionPhase(recentReports).phase,
                emotionPhase(recentReports).label,
                emotionPhase(recentReports).color,
                emotionPhase(recentReports).description,
                riskSignals(report, recentReports)
        );
    }

    // ── 情绪周期 ──

    private record EmotionResult(String phase, String label, String color, String description) {}

    private EmotionResult emotionPhase(List<DailyRecapReport> recentReports) {
        if (recentReports == null || recentReports.size() < 3) {
            return new EmotionResult("unknown", "数据不足", "#94a3b8", "历史数据不足，暂无法判断");
        }

        List<DailyRecapReport> recent = recentReports.size() > 5
                ? recentReports.subList(recentReports.size() - 5, recentReports.size())
                : recentReports;

        List<Double> sealRates = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        List<Integer> limitTotals = new ArrayList<>();

        for (DailyRecapReport r : recent) {
            Double sr = sealRate(r);
            if (sr != null) sealRates.add(sr);
            heights.add(maxBoardHeight(r));
            limitTotals.add(limitUpTotal(r));
        }

        double lastSeal = sealRates.isEmpty() ? 60 : sealRates.get(sealRates.size() - 1);
        int lastHeight = heights.get(heights.size() - 1);
        int lastTotal = limitTotals.get(limitTotals.size() - 1);
        int prevHeight = heights.size() >= 2 ? heights.get(heights.size() - 2) : lastHeight;
        double avgSeal = sealRates.isEmpty() ? 60 : sealRates.stream().mapToDouble(d -> d).average().orElse(60);

        int heightTrend = lastHeight - prevHeight;
        boolean heightRising = heights.size() >= 3 && heights.get(heights.size() - 1) > heights.get(heights.size() - 3);
        boolean heightFalling = heights.size() >= 3 && heights.get(heights.size() - 1) < heights.get(heights.size() - 3);
        boolean sealHigh = lastSeal >= 75;
        boolean sealLow = lastSeal < 55;

        if (sealLow && lastTotal < 30 && lastHeight <= 3) {
            return new EmotionResult("ice", "冰点期", "#64748b", "市场低迷，涨停少封板率低，需要耐心等待");
        }
        if (heightFalling && sealLow) {
            return new EmotionResult("retreat", "退潮期", "#0d9488", "情绪退潮，以观望为主");
        }
        if (heightTrend < 0 && lastHeight >= 3) {
            return new EmotionResult("diverge", "分歧期", "#f59e0b", "多空分歧加大，控制仓位");
        }
        if (sealHigh && lastTotal >= 50 && lastHeight >= 5) {
            return new EmotionResult("climax", "高潮期", "#ef4444", "市场火热，注意高潮后的分歧");
        }
        if (heightRising && lastSeal >= 60) {
            return new EmotionResult("ferment", "发酵期", "#f97316", "情绪升温，高度在抬升，把握主线");
        }
        if (heightTrend >= 0 && lastTotal >= 25 && avgSeal >= 55) {
            return new EmotionResult("repair", "修复期", "#22c55e", "从低点回升，可适当参与低位题材");
        }
        if (lastSeal >= 60) {
            return new EmotionResult("ferment", "发酵期", "#f97316", "情绪升温，高度在抬升，把握主线");
        }
        return new EmotionResult("retreat", "退潮期", "#0d9488", "情绪退潮，以观望为主");
    }

    // ── 风险信号 ──

    private List<RiskSignal> riskSignals(DailyRecapReport report, List<DailyRecapReport> recentReports) {
        List<RiskSignal> signals = new ArrayList<>();

        Double sr = sealRate(report);
        if (sr != null) {
            if (sr < 50) signals.add(new RiskSignal("danger", "封板率仅 " + sr + "%，追高风险极大"));
            else if (sr < 65) signals.add(new RiskSignal("warning", "封板率 " + sr + "%，市场分歧较大"));
            else if (sr >= 80) signals.add(new RiskSignal("safe", "封板率 " + sr + "%，市场一致性良好"));
        }

        int total = limitUpTotal(report);
        if (total < 20) signals.add(new RiskSignal("danger", "涨停仅 " + total + " 家，操作难度极大"));
        else if (total < 35) signals.add(new RiskSignal("warning", "涨停 " + total + " 家，机会偏少"));
        else if (total >= 60) signals.add(new RiskSignal("safe", "涨停 " + total + " 家，赚钱机会较多"));

        Double premium = yesterdayLimitPremium(report);
        if (premium != null) {
            if (premium < -2) signals.add(new RiskSignal("danger", "昨涨停今日溢价 " + premium + "%，亏钱效应明显"));
            else if (premium < 0) signals.add(new RiskSignal("warning", "昨涨停今日溢价 " + premium + "%，谨慎追高"));
            else if (premium >= 2) signals.add(new RiskSignal("safe", "昨涨停今日溢价 +" + premium + "%，赚钱效应良好"));
        }

        Double ratio = limitRatio(report);
        if (ratio != null) {
            if (ratio < 2) signals.add(new RiskSignal("danger", "涨跌停比 " + ratio + ":1，多空力量失衡"));
            else if (ratio >= 5) signals.add(new RiskSignal("safe", "涨跌停比 " + ratio + ":1，多方明显占优"));
        }

        if (recentReports != null && recentReports.size() >= 2) {
            DailyRecapReport prev = recentReports.get(recentReports.size() - 2);
            DailyRecapReport curr = recentReports.get(recentReports.size() - 1);
            int prevMax = maxBoardHeight(prev);
            int currMax = maxBoardHeight(curr);
            if (prevMax - currMax >= 2 && prevMax >= 4) {
                signals.add(new RiskSignal("danger", "最高板从 " + prevMax + "板 降至 " + currMax + "板，高度断裂"));
            }
        }

        return signals;
    }

    // ── 工具方法 ──

    private Double averageChangePercent(List<StockRecord> rows) {
        List<StockRecord> safeRows = safe(rows);
        if (safeRows.isEmpty()) return null;
        double sum = 0;
        int count = 0;
        for (StockRecord r : safeRows) {
            Double v = parsePercent(r.changePercent());
            if (v != null) { sum += v; count++; }
        }
        return count > 0 ? Math.round(sum / count * 100.0) / 100.0 : null;
    }

    private static <T> List<T> safe(List<T> list) {
        return list != null ? list : List.of();
    }
}
