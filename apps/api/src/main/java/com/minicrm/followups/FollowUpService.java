package com.minicrm.followups;

import com.minicrm.common.ActivityLogEvents;
import com.minicrm.common.ActivityLogService;
import com.minicrm.common.ApiException;
import com.minicrm.common.BusinessRules;
import com.minicrm.common.FollowUpType;
import com.minicrm.common.PageSupport;
import com.minicrm.common.SecurityUser;
import com.minicrm.leads.LeadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FollowUpService {
  private final JdbcTemplate jdbc;
  private final LeadService leadService;
  private final ActivityLogService activityLogService;

  public FollowUpService(JdbcTemplate jdbc, LeadService leadService, ActivityLogService activityLogService) {
    this.jdbc = jdbc;
    this.leadService = leadService;
    this.activityLogService = activityLogService;
  }

  public PageSupport.Result<Map<String, Object>> list(SecurityUser actor, UUID leadId, Query query) {
    Map<String, Object> lead = leadService.get(actor, leadId);
    PageSupport.PageRequest paging = PageSupport.request(query.page(), query.pageSize());
    String typeClause = query.type() == null ? "" : " AND f.type = ?::follow_up_type";
    String fromClause = query.from() == null ? "" : " AND f.occurred_at >= ?";
    String toClause = query.to() == null ? "" : " AND f.occurred_at <= ?";
    List<Object> args = new ArrayList<>();
    args.add(actor.organizationId());
    args.add(lead.get("id"));
    if (query.type() != null) args.add(query.type().name());
    if (query.from() != null) args.add(query.from());
    if (query.to() != null) args.add(query.to());
    String where = " WHERE f.organization_id = ? AND f.lead_id = ? AND f.deleted_at IS NULL"
        + typeClause + fromClause + toClause;
    Long total = jdbc.queryForObject("SELECT COUNT(*) FROM follow_ups f" + where, Long.class, args.toArray());
    List<Object> listArgs = new ArrayList<>(args);
    listArgs.add(paging.pageSize());
    listArgs.add(paging.offset());
    List<Map<String, Object>> items = jdbc.query(
        "SELECT f.id, f.lead_id, f.created_by_id, f.type, f.occurred_at, f.summary, f.result, "
            + "f.next_step_at, f.created_at, f.updated_at, u.id AS user_id, u.name AS user_name "
            + "FROM follow_ups f JOIN users u ON u.id = f.created_by_id" + where
            + " ORDER BY f.occurred_at DESC, f.created_at DESC, f.id DESC LIMIT ? OFFSET ?",
        (rs, row) -> row(rs), listArgs.toArray());
    return new PageSupport.Result<>(items, PageSupport.meta(paging, total == null ? 0 : total));
  }

  public Map<String, Object> get(SecurityUser actor, UUID id) {
    Map<String, Object> result = jdbc.query(
        "SELECT f.id, f.organization_id, f.lead_id, f.created_by_id, f.type, f.occurred_at, "
            + "f.summary, f.result, f.next_step_at, f.created_at, f.updated_at, u.id AS user_id, u.name AS user_name "
            + "FROM follow_ups f JOIN users u ON u.id = f.created_by_id "
            + "WHERE f.id = ? AND f.organization_id = ? AND f.deleted_at IS NULL",
        rs -> rs.next() ? row(rs) : null, id, actor.organizationId());
    if (result == null) throw notFound();
    Map<String, Object> lead = leadService.get(actor, (UUID) result.get("leadId"));
    result.put("lead", Map.of("id", lead.get("id"), "name", lead.get("name")));
    return result;
  }

  @Transactional
  public Map<String, Object> create(SecurityUser actor, UUID leadId, CreateRequest request,
                                    HttpServletRequest httpRequest) {
    Map<String, Object> lead = leadService.get(actor, leadId);
    BusinessRules.requireLeadOwnerOrAdmin(actor, (UUID) lead.get("ownerId"));
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO follow_ups (id, organization_id, lead_id, created_by_id, type, occurred_at, summary, result, next_step_at) "
            + "VALUES (?, ?, ?, ?, ?::follow_up_type, ?, ?, ?, ?)",
        id, actor.organizationId(), leadId, actor.id(), request.type().name(), request.occurredAt(),
        request.summary().trim(), blank(request.result()), request.nextStepAt());
    ensureAutoTask(actor, leadId, id, lead, request.summary(), request.nextStepAt(), httpRequest);
    recalculateNextFollowUpAt(actor.organizationId(), leadId);
    activityLogService.record(actor, ActivityLogEvents.CREATE_FOLLOW_UP, "FOLLOW_UP", id, null, httpRequest);
    return get(actor, id);
  }

  @Transactional
  public Map<String, Object> update(SecurityUser actor, UUID id, UpdateRequest request,
                                    HttpServletRequest httpRequest) {
    Map<String, Object> current = get(actor, id);
    UUID leadId = (UUID) current.get("leadId");
    Map<String, Object> lead = leadService.get(actor, leadId);
    UUID ownerId = (UUID) lead.get("ownerId");
    UUID creatorId = (UUID) current.get("createdById");
    if (!BusinessRules.isAdmin(actor) && !actor.id().equals(ownerId) && !actor.id().equals(creatorId)) {
      throw new ApiException("FORBIDDEN", "当前用户没有执行此操作的权限", HttpStatus.FORBIDDEN);
    }
    if (request.occurredAt() != null && !BusinessRules.isAdmin(actor)
        && Duration.between(request.occurredAt(), Instant.now()).toHours() > 24) {
      throw new ApiException("VALIDATION_FAILED", "跟进发生时间不能早于 24 小时", HttpStatus.BAD_REQUEST);
    }
    jdbc.update(
        "UPDATE follow_ups SET type = COALESCE(?::follow_up_type, type), occurred_at = COALESCE(?, occurred_at), "
            + "summary = COALESCE(?, summary), result = COALESCE(?, result), next_step_at = COALESCE(?, next_step_at), "
            + "updated_at = now() WHERE id = ? AND organization_id = ? AND deleted_at IS NULL",
        request.type() == null ? null : request.type().name(), request.occurredAt(), blank(request.summary()),
        blank(request.result()), request.nextStepAt(), id, actor.organizationId());
    String title = request.summary() == null ? String.valueOf(current.get("summary")) : request.summary().trim();
    Instant nextStepAt = request.nextStepAt() == null ? toInstant(current.get("nextStepAt")) : request.nextStepAt();
    ensureAutoTask(actor, leadId, id, lead, title, nextStepAt, httpRequest);
    recalculateNextFollowUpAt(actor.organizationId(), leadId);
    activityLogService.record(actor, ActivityLogEvents.UPDATE_FOLLOW_UP, "FOLLOW_UP", id, null, httpRequest);
    return get(actor, id);
  }

  @Transactional
  public Map<String, Object> delete(SecurityUser actor, UUID id, HttpServletRequest httpRequest) {
    Map<String, Object> current = jdbc.query(
        "SELECT id, lead_id, created_by_id, deleted_at FROM follow_ups WHERE id = ? AND organization_id = ?",
        rs -> rs.next() ? deletedRow(rs) : null, id, actor.organizationId());
    if (current == null) throw notFound();
    UUID leadId = (UUID) current.get("leadId");
    if (!BusinessRules.isAdmin(actor) && !actor.id().equals(current.get("createdById"))) {
      throw new ApiException("FORBIDDEN", "当前用户没有执行此操作的权限", HttpStatus.FORBIDDEN);
    }
    jdbc.update("UPDATE follow_ups SET deleted_at = COALESCE(deleted_at, now()), updated_at = now() "
            + "WHERE id = ? AND organization_id = ?", id, actor.organizationId());
    recalculateNextFollowUpAt(actor.organizationId(), leadId);
    if (current.get("deletedAt") == null) {
      activityLogService.record(actor, ActivityLogEvents.DELETE_FOLLOW_UP, "FOLLOW_UP", id, null, httpRequest);
    }
    return Map.of("deleted", true, "id", id);
  }

  private void ensureAutoTask(SecurityUser actor, UUID leadId, UUID followUpId, Map<String, Object> lead,
                              String summary, Instant dueAt, HttpServletRequest request) {
    if (dueAt == null) return;
    UUID ownerId = (UUID) lead.get("ownerId");
    String reason = ownerReason(actor.organizationId(), ownerId);
    if (reason != null) {
      activityLogService.record(actor, ActivityLogEvents.SKIP_TASK_AUTO_CREATE, "FOLLOW_UP", followUpId,
          Map.of("leadId", leadId, "followUpId", followUpId, "reason", reason), request);
      return;
    }
    String title = summary == null || summary.isBlank() ? "跟进任务" : summary.trim();
    UUID candidateId = UUID.randomUUID();
    int inserted = jdbc.update("INSERT INTO tasks (id, organization_id, lead_id, follow_up_id, assignee_id, created_by_id, title, due_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (follow_up_id) DO NOTHING", candidateId,
        actor.organizationId(), leadId, followUpId, ownerId, actor.id(), title, dueAt);
    if (inserted == 1) {
      activityLogService.record(actor, ActivityLogEvents.CREATE_TASK, "TASK", candidateId,
          Map.of("leadId", leadId, "followUpId", followUpId), request);
    } else {
      UUID taskId = jdbc.query("SELECT id FROM tasks WHERE follow_up_id = ? AND organization_id = ?",
          rs -> rs.next() ? rs.getObject("id", UUID.class) : null, followUpId, actor.organizationId());
      if (taskId == null) return;
      jdbc.update("UPDATE tasks SET due_at = ?, title = ?, assignee_id = ?, updated_at = now() "
              + "WHERE id = ? AND organization_id = ? AND status = 'pending'::task_status",
          dueAt, title, ownerId, taskId, actor.organizationId());
      activityLogService.record(actor, ActivityLogEvents.UPDATE_TASK, "TASK", taskId,
          Map.of("leadId", leadId, "followUpId", followUpId), request);
    }
  }

  private String ownerReason(UUID organizationId, UUID ownerId) {
    if (ownerId == null) return ActivityLogEvents.OWNER_NOT_FOUND;
    Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ? AND organization_id = ?",
        Integer.class, ownerId, organizationId);
    if (found == null || found == 0) return ActivityLogEvents.OWNER_NOT_FOUND;
    Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ? AND organization_id = ? AND is_active = true",
        Integer.class, ownerId, organizationId);
    return active != null && active == 1 ? null : ActivityLogEvents.OWNER_INACTIVE;
  }

  private void recalculateNextFollowUpAt(UUID organizationId, UUID leadId) {
    Instant next = jdbc.queryForObject(
        "SELECT MIN(next_step_at) FROM follow_ups WHERE organization_id = ? AND lead_id = ? "
            + "AND deleted_at IS NULL AND next_step_at IS NOT NULL",
        (rs, rowNum) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
        organizationId, leadId);
    jdbc.update("UPDATE leads SET next_follow_up_at = ?, updated_at = now() WHERE id = ? AND organization_id = ?",
        next, leadId, organizationId);
  }

  private Map<String, Object> row(ResultSet rs) throws SQLException {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", rs.getObject("id")); result.put("leadId", rs.getObject("lead_id"));
    result.put("createdById", rs.getObject("created_by_id")); result.put("type", rs.getString("type"));
    result.put("occurredAt", rs.getObject("occurred_at")); result.put("summary", rs.getString("summary"));
    result.put("result", rs.getString("result")); result.put("nextStepAt", rs.getObject("next_step_at"));
    result.put("createdBy", Map.of("id", rs.getObject("user_id"), "name", rs.getString("user_name")));
    result.put("createdAt", rs.getObject("created_at")); result.put("updatedAt", rs.getObject("updated_at"));
    return result;
  }

  private Map<String, Object> deletedRow(ResultSet rs) throws SQLException {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", rs.getObject("id")); result.put("leadId", rs.getObject("lead_id"));
    result.put("createdById", rs.getObject("created_by_id")); result.put("deletedAt", rs.getObject("deleted_at"));
    return result;
  }

  private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private Instant toInstant(Object value) {
    if (value == null) return null;
    if (value instanceof Instant instant) return instant;
    if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
    return Instant.parse(value.toString());
  }
  private ApiException notFound() { return new ApiException("RESOURCE_NOT_FOUND", "跟进记录不存在", HttpStatus.NOT_FOUND); }

  public record Query(Integer page, Integer pageSize, FollowUpType type, String from, String to) {}
  public record CreateRequest(@NotNull FollowUpType type, @NotNull Instant occurredAt,
                              @NotBlank @Size(max = 500) String summary, @Size(max = 1000) String result,
                              Instant nextStepAt) {}
  public record UpdateRequest(FollowUpType type, Instant occurredAt, @Size(max = 500) String summary,
                              @Size(max = 1000) String result, Instant nextStepAt) {}
}
