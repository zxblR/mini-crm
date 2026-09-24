package com.minicrm.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AiSuggestionRepositoryContractTest {
  @Test
  void upsertQueryContainsExplicitAuditTimestamps() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    AiSuggestionRepository repository = new AiSuggestionRepository(jdbcTemplate, new ObjectMapper());
    UUID organizationId = UUID.randomUUID();
    UUID createdBy = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    AiDtos.LeadContext context = new AiDtos.LeadContext(leadId, "text", "NEW", ownerId, "");
    Instant now = Instant.parse("2026-09-24T04:00:00Z");

    repository.upsert(organizationId, createdBy, context, AiSuggestionType.INTENT_SCORE,
        "a".repeat(64), "intent-score-v1", new ObjectMapper().createObjectNode().put("score", 70),
        "mock-model", now.plusSeconds(3600), now);

    verify(jdbcTemplate).queryForObject(
        contains("created_at, updated_at"), any(RowMapper.class), any(Object[].class));
  }
}
