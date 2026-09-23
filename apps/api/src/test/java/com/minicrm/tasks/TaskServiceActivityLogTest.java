package com.minicrm.tasks;

import com.minicrm.common.ActivityLogEvents;
import com.minicrm.common.ActivityLogService;
import com.minicrm.common.SecurityUser;
import com.minicrm.leads.LeadService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskServiceActivityLogTest {
  @Test
  void completeAndCancelWriteOnlyNonPiiActionLogs() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ActivityLogService logs = mock(ActivityLogService.class);
    TaskService service = new TaskService(jdbc, mock(LeadService.class), logs);
    HttpServletRequest request = mock(HttpServletRequest.class);
    UUID organizationId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    SecurityUser admin = new SecurityUser(UUID.randomUUID(), organizationId, "Admin", "admin@example.com",
        null, List.of("ADMIN"), List.of());

    Map<String, Object> task = new LinkedHashMap<>();
    task.put("id", taskId);
    task.put("status", "pending");
    task.put("assigneeId", UUID.randomUUID());
    doReturn(task).when(jdbc).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));

    service.resolve(admin, taskId, true, null, request);
    service.resolve(admin, taskId, false, null, request);

    verify(logs).record(eq(admin), eq(ActivityLogEvents.COMPLETE_TASK), eq("TASK"), eq(taskId), isNull(), eq(request));
    verify(logs).record(eq(admin), eq(ActivityLogEvents.CANCEL_TASK), eq("TASK"), eq(taskId), isNull(), eq(request));
  }
}
