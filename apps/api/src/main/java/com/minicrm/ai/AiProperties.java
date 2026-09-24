package com.minicrm.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AiProperties {
    @Value("${ai.service-url:http://localhost:8000}")
    private String baseUrl;

    @Value("${ai.service-token:}")
    private String serviceToken;

    @Value("${llm.model:deepseek-chat}")
    private String model;

    @Value("${app.ai.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Value("${app.ai.read-timeout-ms:8000}")
    private int readTimeoutMs;

    @Value("${app.ai.total-budget-ms:10000}")
    private int totalBudgetMs;

    @Value("${app.ai.retry-count:1}")
    private int retryCount;

    @Value("${app.ai.cache-ttl:24h}")
    private Duration cacheTtl;

    public String promptVersion(AiSuggestionType type) {
        String variable = switch (type) {
            case INTENT_SCORE -> "AI_PROMPT_INTENT_SCORE_VERSION";
            case FOLLOW_UP_SUMMARY -> "AI_PROMPT_FOLLOW_UP_SUMMARY_VERSION";
            case NEXT_ACTION -> "AI_PROMPT_NEXT_ACTION_VERSION";
            case SCRIPT -> "AI_PROMPT_SCRIPT_VERSION";
            case WAKE_UP -> "AI_PROMPT_WAKE_UP_VERSION";
        };
        return System.getenv().getOrDefault(variable, "v1");
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String serviceToken() {
        return serviceToken;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public String model() {
        return model;
    }

    public String getModel() {
        return model;
    }

    public int connectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int readTimeoutMs() {
        return readTimeoutMs;
    }

    public int totalBudgetMs() {
        return totalBudgetMs;
    }

    public int retryCount() {
        return retryCount;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Duration cacheTtl() {
        return cacheTtl;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }
}
