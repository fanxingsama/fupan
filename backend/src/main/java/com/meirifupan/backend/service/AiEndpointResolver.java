package com.meirifupan.backend.service;

public final class AiEndpointResolver {

    private AiEndpointResolver() {
    }

    public static String resolveChatCompletionsUrl(String configuredBaseUrl) {
        String base = configuredBaseUrl == null ? "" : configuredBaseUrl.trim();
        if (base.isEmpty()) {
            return base;
        }

        if (base.endsWith("/chat/completions")) {
            return base;
        }

        if (base.endsWith("/v1")) {
            return base + "/chat/completions";
        }

        if (base.endsWith("/v1/")) {
            return base + "chat/completions";
        }

        if (base.endsWith("/text/chatcompletion_v2")) {
            int idx = base.indexOf("/v1/text/chatcompletion_v2");
            if (idx >= 0) {
                return base.substring(0, idx) + "/v1/chat/completions";
            }
        }

        return base;
    }
}
