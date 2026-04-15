package com.meirifupan.backend.model;

import java.util.List;

/**
 * 市场情绪指标 —— 由后端 IndicatorService 根据 DailyRecapReport 计算得出。
 * <p>
 * 包含封板率、涨跌停比、赚钱效应、连板梯队、情绪周期、风险预警等短线核心指标，
 * 前端仅负责展示，不参与任何计算逻辑。
 */
public record MarketIndicators(
        // ── 核心数值指标 ──

        /** 封板率(%) = 封板成功数 / (封板成功数 + 炸板数) × 100 */
        Double sealRate,
        /** 涨跌停比 = 涨停总数 / 跌停总数 */
        Double limitRatio,
        /** 涨停总数（连板 + 首板） */
        int limitUpTotal,
        /** 跌停总数 */
        int limitDownTotal,
        /** 炸板总数 */
        int brokenCount,

        // ── 赚钱效应 ──

        /** 昨日涨停今日平均涨幅(%) */
        Double yesterdayLimitPremium,
        /** 昨日涨停今日上涨比例(%) */
        Double yesterdayLimitWinRate,
        /** 昨日炸板今日平均涨幅(%) */
        Double yesterdayBrokenAvg,

        // ── 连板梯队 ──

        /** 当前最高连板高度 */
        int maxBoardHeight,
        /** 连板梯队明细 [{height, count}] */
        List<LadderLevel> boardLadder,

        // ── 情绪周期 ──

        /** 情绪周期阶段标识：ice/repair/ferment/climax/diverge/retreat/unknown */
        String emotionPhase,
        /** 情绪周期中文标签 */
        String emotionLabel,
        /** 情绪周期颜色编码 */
        String emotionColor,
        /** 情绪周期描述 */
        String emotionDescription,

        // ── 风险信号 ──

        /** 风险预警信号列表 [{level, text}] */
        List<RiskSignal> riskSignals
) {

    /** 连板梯队某一层 */
    public record LadderLevel(int height, int count) {}

    /** 风险信号 */
    public record RiskSignal(String level, String text) {}
}
