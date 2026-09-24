package com.minicrm.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.minicrm.ai.AiSuggestionType;
import java.time.Instant;
import java.util.UUID;

public record AiSuggestionDTO(
    UUID id,
    UUID leadId,
    AiSuggestionType type,
    String promptVersion,
    String model,
    Integer score,
    Double confidence,
    JsonNode result,
    Instant generatedAt,
    Instant expiresAt,
    boolean fromCache,
    boolean degraded) {}
