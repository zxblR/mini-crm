package com.minicrm.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AiDtos {
    private AiDtos() {
    }

    public record GenerateRequest(boolean forceRefresh) {
    }

    public record SuggestionResponse(
            UUID id,
            UUID leadId,
            AiSuggestionType type,
            JsonNode result,
            String model,
            String promptVersion,
            Instant expiresAt,
            boolean cached) {
    }

    public record Envelope<T>(T data, Map<String, Object> meta, ApiError error) {
        public static <T> Envelope<T> ok(T data, Map<String, Object> meta) {
            return new Envelope<>(data, meta, null);
        }

        public static <T> Envelope<T> error(String code, String message) {
            return new Envelope<>(null, Map.of(), new ApiError(code, message));
        }
    }

    public record ApiError(String code, String message) {
    }

    public record LeadContext(UUID leadId, String customerText, String stage, UUID ownerId, String recentFollowUps) {
    }

    public record StoredSuggestion(
            UUID id,
            UUID leadId,
            AiSuggestionType type,
            String inputHash,
            String promptVersion,
            JsonNode result,
            String model,
            Instant expiresAt) {
    }

    public record AiActor(UUID userId, UUID organizationId, boolean owner, boolean admin, boolean sales, boolean support) {
        public boolean canTrigger() {
            return owner || admin || sales;
        }
    }
}
