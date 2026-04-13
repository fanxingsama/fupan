package com.meirifupan.backend.model;

public record StockRecord(
        String code,
        String name,
        String boardHeight,
        String changePercent,
        String price,
        String industry,
        String concept,
        String amount,
        String floatMarketValue,
        String reason,
        String sealAmount,
        String auctionChangePercent,
        String turnoverRate,
        String amplitude,
        String openPrice,
        String extraTag
) {
}
