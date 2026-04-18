package com.meirifupan.backend.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public final class AiJsonSupport {

    private AiJsonSupport() {
    }

    public static String extractJsonObject(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("AI 未返回有效 JSON");
        }
        return text.substring(start, end + 1);
    }

    public static String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    public static List<String> readStringList(JsonNode node, int maxSize) {
        List<String> result = new ArrayList<>();
        if (!node.isArray()) {
            return result;
        }
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                result.add(value);
            }
            if (result.size() >= maxSize) {
                break;
            }
        }
        return result;
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
