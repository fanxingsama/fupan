package com.meirifupan.backend.model;

public record ApiErrorResponse(
        int status,
        String message
) {
}
