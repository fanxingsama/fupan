package com.meirifupan.backend.model;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 采集请求体 —— 前端触发"触发采集"按钮时发送的 POST 请求载荷。
 * <p>
 * 仅包含一个 tradeDate 字段，指定要采集哪个交易日的数据。
 * 后端收到后会调用对应的 MarketRecapProvider 执行采集并落盘。
 */
public record CaptureRequest(
        // 前端要求采集的目标交易日。
        @NotNull LocalDate tradeDate
) {
}
