package com.meirifupan.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StockAiAnalysisRequest(
        @NotBlank(message = "股票代码不能为空")
        @Pattern(regexp = "\\d{6}", message = "股票代码必须是 6 位数字")
        String stockCode,
        @NotBlank(message = "周期不能为空")
        @Pattern(regexp = "1|5|15|30|60|day", message = "仅支持 1/5/15/30/60/day")
        String timeframe
) {
}
