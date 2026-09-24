package com.minicrm.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import org.springframework.http.client.ClientHttpResponse;
import java.util.List;
import java.util.Map;

@Component
public class AiClient {
    private final RestClient restClient;
    private final String serviceToken;
    private final String model;
    private final Duration totalBudget;
    private final Duration readTimeout;

    public AiClient(RestClient aiRestClient, AiProperties properties) {
        this.restClient = aiRestClient;
        this.serviceToken = properties.serviceToken();
        this.model = properties.model();
        this.totalBudget = Duration.ofMillis(properties.totalBudgetMs());
        this.readTimeout = Duration.ofMillis(properties.readTimeoutMs());
    }

    public JsonNode generate(AiSuggestionType type, AiDtos.LeadContext context) {
        String path = switch (type) {
            case INTENT_SCORE -> "/internal/v1/intent-score";
            case FOLLOW_UP_SUMMARY -> "/internal/v1/follow-up-summary";
            case NEXT_ACTION -> "/internal/v1/next-action";
            case SCRIPT -> "/internal/v1/script";
            case WAKE_UP -> "/internal/v1/wake-up";
        };
        Map<String, Object> body = switch (type) {
            case INTENT_SCORE, FOLLOW_UP_SUMMARY -> Map.of(
                    "customer_text", safe(context.customerText()),
                    "recent_follow_ups", List.of(safe(context.recentFollowUps())));
            case NEXT_ACTION -> Map.of(
                    "customer_text", safe(context.customerText()),
                    "recent_follow_ups", List.of(safe(context.recentFollowUps())),
                    "stage", safe(context.stage()));
            case SCRIPT -> Map.of("customer_text", safe(context.customerText()), "next_action", "follow up on the lead");
            case WAKE_UP -> Map.of("customer_text", safe(context.customerText()), "last_follow_up", safe(context.recentFollowUps()));
        };

        Instant deadline = Instant.now().plus(totalBudget);
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode response = restClient.post()
                        .uri(path)
                        .header("Authorization", "Bearer " + serviceToken)
                        .body(body)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                            String responseBody = readResponseBody(clientResponse);
                            throw new AiProviderException(clientResponse.getStatusCode().value(), responseBody);
                        })
                        .body(JsonNode.class);
                if (response == null || !response.isObject()) {
                    throw new AiProviderException(502);
                }
                return response;
            } catch (ResourceAccessException exception) {
                if (attempt == 0 && isConnectionFailure(exception) && canStartAnotherAttempt(deadline)) {
                    continue;
                }
                throw new AiProviderException(503, exception);
      } catch (org.springframework.web.client.UnknownContentTypeException exception) {
        throw new AiProviderException(502, exception);
      } catch (AiProviderException exception) {
                if (attempt == 0 && exception.retryable() && canStartAnotherAttempt(deadline)) {
                    continue;
                }
                throw exception;
            }
        }
        throw new AiProviderException(503);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean canStartAnotherAttempt(Instant deadline) {
        return Instant.now().plus(readTimeout).isBefore(deadline);
    }

    private boolean isConnectionFailure(ResourceAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConnectException || cause instanceof UnknownHostException
                    || cause instanceof NoRouteToHostException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String readResponseBody(ClientHttpResponse response) {
        try {
            return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }

    public String model() {
        return model;
    }

    public static final class AiProviderException extends RuntimeException {
        private final int status;

        public AiProviderException(int status) {
            this(status, (Throwable) null);
        }

        public AiProviderException(int status, Throwable cause) {
            super("AI provider unavailable", cause);
            this.status = status;
        }

        public AiProviderException(int status, String responseBody) {
            super("AI provider returned status " + status + ": " + responseBody);
            this.status = status;
        }

        public boolean retryable() {
            return status == 429 || status == 502 || status == 503 || status == 504;
        }

        public int status() {
            return status;
        }
    }
}
