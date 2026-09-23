package com.minicrm.leads;

import com.minicrm.common.SecurityUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Timeline SQL/integration contract; the real PostgreSQL ordering is part of manual acceptance. */
class LeadRepositoryTimelineIntegrationTest {
  @Test
  void timelineCombinesSourcesAndKeepsDeletedFollowUpsVisible() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    LeadRepository repository = new LeadRepository(jdbc);
    UUID organizationId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    SecurityUser actor = new SecurityUser(UUID.randomUUID(), organizationId, "Admin", "admin@example.com",
        null, List.of("ADMIN"), List.of());

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("id", leadId);
    detail.put("ownerId", actor.id());
    doReturn(detail).when(jdbc).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0, String.class);
      if (sql.contains("FROM tags")) return List.of();
      if (sql.contains("SELECT timeline.id")) {
        return List.of(Map.of(
            "id", UUID.randomUUID(), "type", "follow_up", "title", "跟进记录",
            "description", "deleted follow-up", "actorName", "Admin",
            "occurredAt", Instant.parse("2026-09-22T00:00:00Z"), "deleted", true));
      }
      return List.of();
    });

    Map<String, Object> result = repository.findVisible(actor, leadId, 1, 20);

    Map<?, ?> timelineItem = (Map<?, ?>) ((List<?>) result.get("timeline")).get(0);
    assertThat(timelineItem.get("deleted")).isEqualTo(true);
    // The query is built with UNION ALL and the deterministic three-column ordering contract.
    org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.atLeastOnce())
        .query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
    String timelineSql = sqlCaptor.getAllValues().stream()
        .filter(sql -> sql.contains("SELECT timeline.id"))
        .findFirst().orElseThrow();
    assertThat(timelineSql).contains("UNION ALL")
        .contains("occurred_at DESC, timeline.created_at DESC, timeline.id DESC")
        .contains("LIMIT ? OFFSET ?");
  }
}
