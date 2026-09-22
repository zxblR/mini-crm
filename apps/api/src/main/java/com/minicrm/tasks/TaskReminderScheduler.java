package com.minicrm.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Claims overdue reminders in small batches so multiple API instances do not process the same task. */
@Component
@ConditionalOnProperty(prefix = "app.scheduler.task-reminder", name = "enabled", havingValue = "true")
public class TaskReminderScheduler {
  private static final Logger log = LoggerFactory.getLogger(TaskReminderScheduler.class);

  private final JdbcTemplate jdbc;
  private final int batchSize;

  public TaskReminderScheduler(JdbcTemplate jdbc,
      @Value("${app.scheduler.task-reminder.batch-size:100}") int batchSize) {
    this.jdbc = jdbc;
    this.batchSize = Math.max(1, Math.min(500, batchSize));
  }

  @Scheduled(fixedDelayString = "${app.scheduler.task-reminder.fixed-delay-ms:60000}")
  @Transactional
  public void scanOverdueTasks() {
    List<Map<String, Object>> tasks = jdbc.queryForList(
        "SELECT id, organization_id, lead_id FROM tasks "
            + "WHERE status = 'pending'::task_status AND due_at < now() AND reminder_sent_at IS NULL "
            + "ORDER BY due_at, id LIMIT ? FOR UPDATE SKIP LOCKED", batchSize);
    for (Map<String, Object> task : tasks) {
      UUID id = (UUID) task.get("id");
      jdbc.update("UPDATE tasks SET reminder_sent_at = now(), updated_at = now() "
              + "WHERE id = ? AND reminder_sent_at IS NULL", id);
    }
    if (!tasks.isEmpty()) log.debug("Claimed {} overdue task reminders", tasks.size());
  }
}
