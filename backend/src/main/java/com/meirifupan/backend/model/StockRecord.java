package com.meirifupan.backend.model;

/**
 * 个股记录 —— 系统中最通用的股票数据载体。
 * <p>
 * 在不同的业务场景下复用：
 * <ul>
 *   <li>炸板票（当日/昨日反馈）：boardHeight 留空，extraTag 可能为封板时间</li>
 *   <li>连板票：boardHeight 为连板高度（2、3、4…）</li>
 *   <li>首板票：boardHeight 为 1</li>
 *   <li>跌停票：changePercent 为负值</li>
 *   <li>10 日涨幅前列：changePercent 为 10 日累计涨幅</li>
 * </ul>
 * 所有金额/涨幅字段均以字符串存储（如 "12.50%"、"3.20亿"），
 * 前端解析时通过 parseNumericValue() 转换为可比较的数值。
 */
public record StockRecord(
        // 六位股票代码。
        String code,
        // 股票名称。
        String name,
        // 连板高度；炸板票这里会留空。
        String boardHeight,
        // 涨跌幅。
        String changePercent,
        // 最新价或收盘价。
        String price,
        // 所属行业。
        String industry,
        // 概念展示字段。
        String concept,
        // 成交额。
        String amount,
        // 流通市值。
        String floatMarketValue,
        // 题材原因或状态说明。
        String reason,
        // 封板金额。
        String sealAmount,
        // 竞价涨幅。
        String auctionChangePercent,
        // 换手率。
        String turnoverRate,
        // 振幅。
        String amplitude,
        // 开盘价。
        String openPrice,
        // 额外标签，例如封板时间或跟踪说明。
        String extraTag
) {
}
