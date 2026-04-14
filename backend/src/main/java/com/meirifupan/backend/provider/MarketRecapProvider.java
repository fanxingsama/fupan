package com.meirifupan.backend.provider;

import com.meirifupan.backend.model.DailyRecapReport;

import java.time.LocalDate;

/**
 * 市场复盘数据提供者接口 —— 抽象了“从哪里获取复盘数据”的能力。
 * <p>
 * 目前有两个实现：
 * <ul>
 *   <li>{@link AkshareMarketRecapProvider} —— 通过调用 Python 脚本 + AKShare 采集真实市场数据</li>
 *   <li>{@link MockMarketRecapProvider} —— 返回硬编码的模拟数据，用于开发和演示</li>
 * </ul>
 * 通过 application.yml 中的 recap.provider 配置项来选择启用哪个实现。
 */
public interface MarketRecapProvider {

    /**
     * 数据源名称，对应配置文件里的 recap.provider。
     */
    String name();

    /**
     * 生成指定交易日的完整复盘报告。
     */
    DailyRecapReport capture(LocalDate tradeDate);
}
