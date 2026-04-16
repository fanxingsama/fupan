package com.meirifupan.backend.model;

import java.time.OffsetDateTime;
import java.util.List;

public record MarketIntelligence(
        String tradeDate,
        OffsetDateTime generatedAt,
        List<SourceStat> sourceStats,
        List<TopicPulse> topicPulses,
        List<HotStock> hotStocks,
        List<ThemeCluster> themeClusters,
        List<NewsItem> marketNews,
        List<NewsItem> stockNews,
        List<FeedItem> feedItems
) {

    public record SourceStat(
            String source,
            String category,
            int itemCount
    ) {}

    public record TopicPulse(
            String name,
            int heat,
            String source,
            String sampleStock
    ) {}

    public record HotStock(
            int rank,
            String code,
            String name,
            String price,
            String changePercent,
            List<String> keywords
    ) {}

    public record ThemeCluster(
            String name,
            int heat,
            List<String> sources,
            List<String> sampleTitles,
            List<String> relatedStocks
    ) {}

    public record NewsItem(
            String title,
            String summary,
            String source,
            String publishedAt,
            String relatedCode,
            String relatedName,
            String url
    ) {}

    public record FeedItem(
            String id,
            String type,
            String title,
            String summary,
            String source,
            String publishedAt,
            String relatedCode,
            String relatedName,
            String url,
            List<String> tags,
            int heat
    ) {}
}
