package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meirifupan.backend.config.AiProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class AiRequestCacheService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    public AiRequestCacheService(JdbcTemplate jdbc, ObjectMapper objectMapper, AiProperties aiProperties) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
    }

    public <T> Optional<T> load(String cacheKey, Class<T> type) {
        List<T> cached = jdbc.query(
                "SELECT response_json FROM ai_request_cache WHERE cache_key = ?",
                (rs, rowNum) -> {
                    try {
                        return objectMapper.readValue(rs.getString("response_json"), type);
                    } catch (Exception ex) {
                        throw new IllegalStateException("读取 AI 缓存失败: " + cacheKey, ex);
                    }
                },
                cacheKey
        );
        return cached.isEmpty() ? Optional.empty() : Optional.ofNullable(cached.get(0));
    }

    public void save(String cacheKey, String scenario, Object response) {
        try {
            jdbc.update(
                    "INSERT INTO ai_request_cache (cache_key, scenario, response_json, created_at) VALUES (?, ?, ?, ?) " +
                            "ON CONFLICT(cache_key) DO UPDATE SET response_json = excluded.response_json, created_at = excluded.created_at",
                    cacheKey,
                    scenario,
                    objectMapper.writeValueAsString(response),
                    OffsetDateTime.now().toString()
            );
        } catch (Exception ignored) {
        }
    }

    public String buildCacheKey(String scenario, String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = String.join("\n",
                    scenario,
                    nullToEmpty(aiProperties.provider()),
                    nullToEmpty(aiProperties.baseUrl()),
                    nullToEmpty(aiProperties.model()),
                    payload == null ? "" : payload
            );
            return scenario + ":" + HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成 AI 缓存键", ex);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
