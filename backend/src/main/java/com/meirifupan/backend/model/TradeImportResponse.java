package com.meirifupan.backend.model;

import java.util.List;

public record TradeImportResponse(
        int importedCount,
        int skippedCount,
        List<String> warnings
) {
}
