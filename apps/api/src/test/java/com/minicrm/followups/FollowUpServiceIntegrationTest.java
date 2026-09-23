package com.minicrm.followups;

import com.minicrm.common.ActivityLogEvents;
import com.minicrm.common.ActivityLogService;
import com.minicrm.common.FollowUpType;
import com.minicrm.common.SecurityUser;
import com.minicrm.leads.LeadService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** JdbcTemplate-backed service contract tests; the real PostgreSQL run remains a manual acceptance step. */
class FollowUpServiceIntegrationTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final LeadService leadService = mock(LeadService.class);
  private final ActivityLogService activityLogService = mock(ActivityLogService.class);
  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final FollowUpService service = new FollowUpService(jdbc, leadService, activityLogService);

  private final UUID organizationId = UUID.randomUUID();
  private final UUID actorId = UUID.randomUUID();
  private final UUID leadId = UUID.randomUUID();
  private final SecurityUser actor = new SecurityUser(actorId, organizationId, "Sales", "sales@example.com",
      null, List.of("ADMIN"), List.of());
  private final UUID ownerId = UUID.randomUUID();
  private final Instant nextStepAt = Instant.parse("2026-09-30T00:00:00Z");

  @BeforeEach
  void stubs() {
    when(leadService.get(eq(actor), eq(leadId))).thenReturn(Map.of("id", leadId, "ownerId", ownerId, "name", "Lead"));
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
    doReturn(nextStepAt).when(jdbc).queryForObject(anyString(),
        org.mockito.ArgumentMatchers.<RowMapper<Instant>>any(), any(Object[].class));
  }

  @Test
  void createUsesFollowUpIdConflictGuardAndCreatesOneTask() {
    doAnswer(invocation -> {
      String sql = invocation.getArgument(0, String.class);
      return sql.contains("FROM follow_ups f") ? followUpRow() : null;
    }).when(jdbc).query(anyString(),
        org.mockito.ArgumentMatchers.<ResultSetExtractor<Map<String, Object>>>any(), any(Object[].class));
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    service.create(actor, leadId, new FollowUpService.CreateRequest(
        FollowUpType.call, Instant.parse("2026-09-22T00:00:00Z"), "Call", null, nextStepAt), request);

    assertUpdateTimes(3,
        new ExpectedJdbcTime("INSERT INTO follow_ups", 5, Instant.parse("2026-09-22T00:00:00Z")),
        new ExpectedJdbcTime("INSERT INTO tasks", 7, nextStepAt),
        new ExpectedJdbcTime("UPDATE leads SET next_follow_up_at", 0, nextStepAt));
    verify(activityLogService).record(eq(actor), eq(ActivityLogEvents.CREATE_TASK), eq("TASK"),
        any(UUID.class), org.mockito.ArgumentMatchers.argThat(metadata -> {
          assertThat(metadata).containsOnlyKeys("leadId", "followUpId");
          return true;
        }), eq(request));
    verify(activityLogService).record(eq(actor), eq(ActivityLogEvents.CREATE_FOLLOW_UP), eq("FOLLOW_UP"),
        any(UUID.class), isNull(), eq(request));
  }

  @Test
  void updateBindsFollowUpAndAutoTaskTimesAsOffsetDateTime() {
    doAnswer(invocation -> {
      String sql = invocation.getArgument(0, String.class);
      return sql.contains("FROM follow_ups f") ? followUpRow() : null;
    }).when(jdbc).query(anyString(),
        org.mockito.ArgumentMatchers.<ResultSetExtractor<Map<String, Object>>>any(), any(Object[].class));
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    Instant updatedAt = Instant.parse("2026-10-01T09:30:00Z");

    service.update(actor, UUID.randomUUID(), new FollowUpService.UpdateRequest(
        FollowUpType.call, updatedAt, "Updated call", null, updatedAt), request);

    assertUpdateTimes(3,
        new ExpectedJdbcTime("UPDATE follow_ups SET", 1, updatedAt),
        new ExpectedJdbcTime("INSERT INTO tasks", 7, updatedAt),
        new ExpectedJdbcTime("UPDATE leads SET next_follow_up_at", 0, nextStepAt));
  }

  @Test
  void existingAutoTaskUpdateBindsDueAtAsOffsetDateTime() {
    UUID existingTaskId = UUID.randomUUID();
    doAnswer(invocation -> {
      String sql = invocation.getArgument(0, String.class);
      if (sql.contains("FROM follow_ups f")) return followUpRow();
      if (sql.contains("SELECT id FROM tasks")) return existingTaskId;
      return null;
    }).when(jdbc).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));
    when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(invocation ->
        invocation.getArgument(0, String.class).contains("INSERT INTO tasks") ? 0 : 1);

    service.create(actor, leadId, new FollowUpService.CreateRequest(
        FollowUpType.call, Instant.parse("2026-09-22T00:00:00Z"), "Call", null, nextStepAt), request);

    assertUpdateTimes(4, new ExpectedJdbcTime("UPDATE tasks SET due_at", 0, nextStepAt));
  }

  @Test
  void deleteRecalculatesUsingOnlyNonDeletedFollowUps() {
    Map<String, Object> deletedRow = new LinkedHashMap<>();
    deletedRow.put("id", UUID.randomUUID());
    deletedRow.put("leadId", leadId);
    deletedRow.put("createdById", actorId);
    deletedRow.put("deletedAt", null);
    doReturn(deletedRow).when(jdbc).query(anyString(),
        org.mockito.ArgumentMatchers.<ResultSetExtractor<Map<String, Object>>>any(), any(Object[].class));

    service.delete(actor, (UUID) deletedRow.get("id"), request);

    verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.contains("deleted_at IS NULL"),
        org.mockito.ArgumentMatchers.<RowMapper<Instant>>any(), eq(organizationId), eq(leadId));
  }

  @Test
  void inactiveOwnerSkipsTaskAndWritesOnlyRequiredMetadata() {
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
        .thenReturn(1, 0);
    doAnswer(invocation -> {
      String sql = invocation.getArgument(0, String.class);
      return sql.contains("FROM follow_ups f") ? followUpRow() : null;
    }).when(jdbc).query(anyString(),
        org.mockito.ArgumentMatchers.<ResultSetExtractor<Map<String, Object>>>any(), any(Object[].class));

    service.create(actor, leadId, new FollowUpService.CreateRequest(
        FollowUpType.call, Instant.parse("2026-09-22T00:00:00Z"), "Call", null, nextStepAt), request);

    verify(activityLogService).record(eq(actor), eq(ActivityLogEvents.SKIP_TASK_AUTO_CREATE),
        eq("FOLLOW_UP"), any(UUID.class), org.mockito.ArgumentMatchers.argThat(metadata -> {
          assertThat(metadata).containsOnlyKeys("leadId", "followUpId", "reason");
          assertThat(metadata.get("reason")).isEqualTo(ActivityLogEvents.OWNER_INACTIVE);
          return true;
        }), eq(request));
    verifyNoInteractionsForTaskInsert();
  }

  private void verifyNoInteractionsForTaskInsert() {
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never())
        .update(org.mockito.ArgumentMatchers.contains("INSERT INTO tasks"), any(Object[].class));
  }

  private void assertUpdateTimes(int updateCount, ExpectedJdbcTime... expectedTimes) {
    org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.ArgumentCaptor<Object[]> argsCaptor = org.mockito.ArgumentCaptor.forClass(Object[].class);
    verify(jdbc, org.mockito.Mockito.times(updateCount)).update(sqlCaptor.capture(), argsCaptor.capture());
    List<String> sqlCalls = sqlCaptor.getAllValues();
    List<Object[]> argumentCalls = argsCaptor.getAllValues();
    for (ExpectedJdbcTime expected : expectedTimes) {
      int callIndex = -1;
      for (int index = 0; index < sqlCalls.size(); index++) {
        if (sqlCalls.get(index).contains(expected.sqlPart())) {
          callIndex = index;
          break;
        }
      }
      assertThat(callIndex).as("SQL containing %s", expected.sqlPart()).isGreaterThanOrEqualTo(0);
      Object[] args = argumentCalls.get(callIndex);
      assertThat(args).hasSizeGreaterThan(expected.argumentIndex());
      assertThat(args[expected.argumentIndex()])
          .isInstanceOf(OffsetDateTime.class)
          .isEqualTo(OffsetDateTime.ofInstant(expected.instant(), java.time.ZoneOffset.UTC));
    }
  }

  private record ExpectedJdbcTime(String sqlPart, int argumentIndex, Instant instant) {}

  private Map<String, Object> followUpRow() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", UUID.randomUUID());
    row.put("leadId", leadId);
    row.put("createdById", actorId);
    row.put("type", "call");
    row.put("occurredAt", Instant.parse("2026-09-22T00:00:00Z"));
    row.put("summary", "Call");
    row.put("result", null);
    row.put("nextStepAt", nextStepAt);
    row.put("createdBy", Map.of("id", actorId, "name", "Sales"));
    row.put("createdAt", Instant.parse("2026-09-22T00:00:00Z"));
    row.put("updatedAt", Instant.parse("2026-09-22T00:00:00Z"));
    return row;
  }
}
