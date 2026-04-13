package com.meirifupan.backend.model;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CaptureRequest(
        @NotNull LocalDate tradeDate
) {
}
