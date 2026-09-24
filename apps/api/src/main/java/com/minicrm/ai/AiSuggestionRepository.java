package com.minicrm.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicrm.common.JdbcTimeUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AiSuggestionRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AiSuggestionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<AiDtos.LeadContext> findLeadContext(UUID organizationId, UUID leadId) {
        String sql = """
                SELECT l.id, l.notes, l.status::text AS status, l.owner_id,
                       COALESCE(string_agg(f.summary, E'\\n' ORDER BY f.occurred_at DESC), '') AS recent_follow_ups
                  FROM leads l
                  LEFT JOIN follow_ups f ON f.lead_id = l.id AND f.organization_id = l.organization_id
                                           AND f.deleted_at IS NULL
                 WHERE l.organization_id = ? AND l.id = ?
                 GROUP BY l.id, l.notes, l.status, l.owner_id
                """;
        List<AiDtos.LeadContext> rows = jdbcTemplate.query(sql, this::mapLead, organizationId, leadId);
        return rows.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findOwner(UUID organizationId, UUID leadId) {
        return jdbcTemplate.query(
                "SELECT owner_id FROM leads WHERE organization_id = ? AND id = ?",
                rs -> rs.next() ? Optional.ofNullable(rs.getObject("owner_id", UUID.class)) : Optional.empty(),
                organizationId,
                leadId);
    }

    @Transactional(readOnly = true)
    public Optional<AiDtos.StoredSuggestion> findFresh(UUID organizationId, UUID leadId, AiSuggestionType type,
                                                        String inputHash, String promptVersion, Instant now) {
        String sql = """
                SELECT id, lead_id, suggestion_type, input_hash, prompt_version, result, model, expires_at
                  FROM ai_suggestions
                 WHERE organization_id = ? AND lead_id = ? AND suggestion_type = ?::ai_suggestion_type
                   AND input_hash = ? AND prompt_version = ? AND expires_at > ?
                """;
        List<AiDtos.StoredSuggestion> rows = jdbcTemplate.query(sql, this::mapSuggestion,
                organizationId, leadId, type.name(), inputHash, promptVersion, JdbcTimeUtils.toDbTime(now));
        return rows.stream().findFirst();
    }

    @Transactional
    public AiDtos.StoredSuggestion upsert(UUID organizationId, UUID createdBy, AiDtos.LeadContext context,
                                           AiSuggestionType type, String inputHash, String promptVersion,
                                           JsonNode result, String model, Instant expiresAt, Instant now) {
        UUID id = UUID.randomUUID();
        String sql = """
                INSERT INTO ai_suggestions
                  (id, organization_id, lead_id, created_by, suggestion_type, input_hash, prompt_version,
                   result, model, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?::ai_suggestion_type, ?, ?, ?::jsonb, ?, ?, ?, ?)
                ON CONFLICT (organization_id, lead_id, suggestion_type, input_hash, prompt_version)
                DO UPDATE SET result = EXCLUDED.result, model = EXCLUDED.model,
                              expires_at = EXCLUDED.expires_at, updated_at = EXCLUDED.updated_at
                RETURNING id, lead_id, suggestion_type, input_hash, prompt_version, result, model, expires_at
                """;
        return jdbcTemplate.queryForObject(sql, this::mapSuggestion, id, organizationId, context.leadId(), createdBy,
                type.name(), inputHash, promptVersion, writeJson(result), model,
                JdbcTimeUtils.toDbTime(expiresAt), JdbcTimeUtils.toDbTime(now), JdbcTimeUtils.toDbTime(now));
    }

    @Transactional(readOnly = true)
    public Optional<AiDtos.StoredSuggestion> findLatest(UUID organizationId, UUID leadId, AiSuggestionType type) {
        String sql = """
                SELECT id, lead_id, suggestion_type, input_hash, prompt_version, result, model, expires_at
                  FROM ai_suggestions
                 WHERE organization_id = ? AND lead_id = ? AND suggestion_type = ?::ai_suggestion_type
                 ORDER BY updated_at DESC LIMIT 1
                """;
        return jdbcTemplate.query(sql, this::mapSuggestion, organizationId, leadId, type.name()).stream().findFirst();
    }

    private AiDtos.LeadContext mapLead(ResultSet rs, int ignored) throws SQLException {
        return new AiDtos.LeadContext(rs.getObject("id", UUID.class), rs.getString("notes"),
                rs.getString("status"), rs.getObject("owner_id", UUID.class), rs.getString("recent_follow_ups"));
    }

    private AiDtos.StoredSuggestion mapSuggestion(ResultSet rs, int ignored) throws SQLException {
        try {
            return new AiDtos.StoredSuggestion(rs.getObject("id", UUID.class), rs.getObject("lead_id", UUID.class),
                    AiSuggestionType.valueOf(rs.getString("suggestion_type")), rs.getString("input_hash"),
                    rs.getString("prompt_version"), objectMapper.readTree(rs.getString("result")),
                    rs.getString("model"), JdbcTimeUtils.fromDbTime(rs, "expires_at"));
        } catch (JsonProcessingException exception) {
            throw new SQLException("invalid stored AI result", exception);
        }
    }

    private String writeJson(JsonNode result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("invalid AI result", exception);
        }
    }
}
