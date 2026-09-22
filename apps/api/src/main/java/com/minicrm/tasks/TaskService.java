package com.minicrm.tasks;

import com.minicrm.common.ActivityLogService;
import com.minicrm.common.ActivityLogEvents;
import com.minicrm.common.ApiException;
import com.minicrm.common.BusinessRules;
import com.minicrm.common.PageSupport;
import com.minicrm.common.SecurityUser;
import com.minicrm.common.TaskStatus;
import com.minicrm.leads.LeadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskService {
  private final JdbcTemplate jdbc;
  private final LeadService leadService;
  private final ActivityLogService activityLogService;

  public TaskService(JdbcTemplate jdbc, LeadService leadService, ActivityLogService activityLogService) {
    this.jdbc = jdbc; this.leadService = leadService; this.activityLogService = activityLogService;
  }

  public PageSupport.Result<Map<String, Object>> list(SecurityUser actor, Query query) {
    if (query.assigneeId() != null && BusinessRules.isSales(actor) && !query.assigneeId().equals(actor.id())) throw new ApiException("FORBIDDEN", "销售只能查看自己的任务", HttpStatus.FORBIDDEN);
    ensureUser(query.assigneeId(), actor.organizationId());
    if (query.leadId() != null) leadService.get(actor, query.leadId());
    PageSupport.PageRequest paging = PageSupport.request(query.page(), query.pageSize());
    StringBuilder where = new StringBuilder(" WHERE t.organization_id = ? "); List<Object> args = new ArrayList<>(); args.add(actor.organizationId());
    if (BusinessRules.isSales(actor)) { where.append(" AND t.assignee_id = ? "); args.add(actor.id()); }
    if (query.status() != null && !query.status().isBlank()) {
      if ("overdue".equals(query.status())) where.append(" AND t.status = 'pending'::task_status AND t.due_at < now() ");
      else { try { TaskStatus.valueOf(query.status()); } catch (IllegalArgumentException e) { throw new ApiException("VALIDATION_FAILED", "任务状态无效", HttpStatus.BAD_REQUEST); } where.append(" AND t.status = ?::task_status "); args.add(query.status()); }
    }
    if (query.dueFrom() != null) { where.append(" AND t.due_at >= ? "); args.add(query.dueFrom()); }
    if (query.dueTo() != null) { where.append(" AND t.due_at <= ? "); args.add(query.dueTo()); }
    if (query.assigneeId() != null) { where.append(" AND t.assignee_id = ? "); args.add(query.assigneeId()); }
    if (query.leadId() != null) { where.append(" AND t.lead_id = ? "); args.add(query.leadId()); }
    Long total = jdbc.queryForObject("SELECT COUNT(*) FROM tasks t" + where, Long.class, args.toArray());
    List<Object> listArgs = new ArrayList<>(args); listArgs.add(paging.pageSize()); listArgs.add(paging.offset());
    List<Map<String, Object>> items = jdbc.query("SELECT t.id, t.lead_id, l.name AS lead_name, t.title, t.due_at, t.status, t.resolution_note, t.completed_at, t.cancelled_at, t.created_at, t.assignee_id, u.name AS assignee_name FROM tasks t JOIN leads l ON l.id = t.lead_id JOIN users u ON u.id = t.assignee_id" + where + " ORDER BY t.due_at ASC LIMIT ? OFFSET ?", (rs, row) -> task(rs), listArgs.toArray());
    return new PageSupport.Result<>(items, PageSupport.meta(paging, total == null ? 0 : total));
  }

  public PageSupport.Result<Map<String, Object>> today(SecurityUser actor, Integer page, Integer pageSize) {
    Instant start = Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    Instant end = start.plus(java.time.Duration.ofDays(1));
    return list(actor, new Query(page, pageSize, "pending", start.toString(),
        end.minusMillis(1).toString(), null, null));
  }

  public Map<String, Object> get(SecurityUser actor, UUID id) {
    Map<String, Object> task = jdbc.query("SELECT t.id, t.organization_id, t.lead_id, l.name AS lead_name, t.title, t.due_at, t.status, t.resolution_note, t.completed_at, t.cancelled_at, t.created_at, t.assignee_id, u.name AS assignee_name FROM tasks t JOIN leads l ON l.id = t.lead_id JOIN users u ON u.id = t.assignee_id WHERE t.id = ? AND t.organization_id = ?", rs -> rs.next() ? task(rs) : null, id, actor.organizationId());
    if (task == null) throw notFound();
    if (BusinessRules.isSales(actor) && !actor.id().equals(task.get("assigneeId"))) throw notFound();
    return task;
  }

  @Transactional
  public Map<String, Object> update(SecurityUser actor, UUID id, UpdateRequest request, HttpServletRequest httpRequest) {
    Map<String, Object> task = get(actor, id);
    if (!canMutate(actor, (UUID) task.get("assigneeId"))) throw forbidden();
    if (!"pending".equals(task.get("status"))) throw new ApiException("TASK_TERMINAL_STATE", "已完成或已取消任务不可修改", HttpStatus.CONFLICT);
    if (!BusinessRules.isAdmin(actor) && request.assigneeId() != null && !actor.id().equals(request.assigneeId())) throw forbidden();
    if (request.assigneeId() != null) ensureUser(request.assigneeId(), actor.organizationId());
    jdbc.update("UPDATE tasks SET title = COALESCE(?, title), due_at = COALESCE(?, due_at), assignee_id = COALESCE(?, assignee_id), updated_at = now() WHERE id = ? AND organization_id = ?", blank(request.title()), request.dueAt(), request.assigneeId(), id, actor.organizationId());
    activityLogService.record(actor, ActivityLogEvents.UPDATE_TASK, "TASK", id, null, httpRequest);
    return get(actor, id);
  }

  @Transactional
  public Map<String, Object> resolve(SecurityUser actor, UUID id, boolean complete, String note, HttpServletRequest httpRequest) {
    Map<String, Object> task = get(actor, id);
    if (!canMutate(actor, (UUID) task.get("assigneeId"))) throw forbidden();
    String current = String.valueOf(task.get("status"));
    String target = complete ? "completed" : "cancelled";
    if (target.equals(current)) throw new ApiException(complete ? "TASK_ALREADY_COMPLETED" : "TASK_ALREADY_CANCELLED", complete ? "任务已经完成" : "任务已经取消", HttpStatus.CONFLICT);
    if (!"pending".equals(current)) throw new ApiException("TASK_TERMINAL_STATE", "任务已经处于终态", HttpStatus.CONFLICT);
    jdbc.update("UPDATE tasks SET status = ?::task_status, resolution_note = ?, completed_at = ?, cancelled_at = ?, updated_at = now() WHERE id = ? AND organization_id = ?", target, blank(note), complete ? java.sql.Timestamp.from(Instant.now()) : null, complete ? null : java.sql.Timestamp.from(Instant.now()), id, actor.organizationId());
    activityLogService.record(actor, complete ? ActivityLogEvents.COMPLETE_TASK : ActivityLogEvents.CANCEL_TASK,
        "TASK", id, null, httpRequest);
    return get(actor, id);
  }

  private void ensureUser(UUID id, UUID organizationId) {
    if (id == null) return;
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ? AND organization_id = ?", Integer.class, id, organizationId);
    if (count == null || count != 1) throw new ApiException("RESOURCE_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
  }
  private Map<String, Object> task(ResultSet rs) throws SQLException { Map<String, Object> result = new LinkedHashMap<>(); result.put("id", rs.getObject("id")); result.put("leadId", rs.getObject("lead_id")); result.put("leadName", rs.getString("lead_name")); result.put("title", rs.getString("title")); result.put("dueAt", rs.getObject("due_at")); result.put("status", rs.getString("status")); result.put("isOverdue", "pending".equals(rs.getString("status")) && rs.getTimestamp("due_at").toInstant().isBefore(Instant.now())); result.put("assigneeId", rs.getObject("assignee_id")); result.put("assignee", Map.of("id", rs.getObject("assignee_id"), "name", rs.getString("assignee_name"))); result.put("resolutionNote", rs.getString("resolution_note")); result.put("completedAt", rs.getObject("completed_at")); result.put("cancelledAt", rs.getObject("cancelled_at")); result.put("createdAt", rs.getObject("created_at")); return result; }
  private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private ApiException notFound() { return new ApiException("RESOURCE_NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND); }
  private ApiException forbidden() { return new ApiException("FORBIDDEN", "当前用户没有执行此操作的权限", HttpStatus.FORBIDDEN); }
  private boolean canMutate(SecurityUser actor, UUID assigneeId) {
    return BusinessRules.isAdmin(actor)
        || (BusinessRules.isSales(actor) && actor.id().equals(assigneeId));
  }
  public record Query(Integer page, Integer pageSize, String status, String dueFrom, String dueTo, UUID assigneeId, UUID leadId) {}
  public record UpdateRequest(@Size(max = 200) String title, Instant dueAt, UUID assigneeId) {}
}
