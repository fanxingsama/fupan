package com.meirifupan.backend.model;

/**
 * 主页展示的板块指数快照。
 */
public record BoardIndexSnapshot(
        String key,
        String label,
        String code,
        String latest,
        String changeAmount,
        String changePercent
) {
}
