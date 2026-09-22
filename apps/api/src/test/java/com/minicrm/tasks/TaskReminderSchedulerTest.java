package com.minicrm.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskReminderSchedulerTest {
  @Test
  void claimsPendingOverdueTasksWithSkipLocked() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID taskId = UUID.randomUUID();
    when(jdbc.queryForList(contains("FOR UPDATE SKIP LOCKED"), eq(100)))
        .thenReturn(List.of(Map.of("id", taskId)));

    new TaskReminderScheduler(jdbc, 100).scanOverdueTasks();

    verify(jdbc).update(contains("reminder_sent_at"), eq(taskId));
  }
}
