package com.minicrm.tasks;

import com.minicrm.common.ActivityLogService;
import com.minicrm.common.JdbcTimeUtils;
import com.minicrm.common.SecurityUser;
import com.minicrm.leads.LeadService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskServiceJdbcTimeTest {
  @Test
  void updateBindsDueAtAsOffsetDateTime() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID organizationId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    Map<String, Object> task = new LinkedHashMap<>();
    task.put("id", taskId);
    task.put("status", "pending");
    task.put("assigneeId", actorId);
    doReturn(task).when(jdbc).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));
    SecurityUser admin = new SecurityUser(actorId, organizationId, "Admin", "admin@example.com",
        null, List.of("ADMIN"), List.of());
    Instant dueAt = Instant.parse("2026-09-30T12:00:00Z");

    new TaskService(jdbc, mock(LeadService.class), mock(ActivityLogService.class)).update(
        admin, taskId, new TaskService.UpdateRequest(null, dueAt, null), mock(HttpServletRequest.class));

    org.mockito.ArgumentCaptor<Object[]> args = org.mockito.ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(anyString(), args.capture());
    assertThat(args.getValue()).contains(JdbcTimeUtils.toDbTime(dueAt));
    assertThat(args.getValue()).anyMatch(OffsetDateTime.class::isInstance);
  }
}
