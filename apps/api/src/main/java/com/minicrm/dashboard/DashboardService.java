package com.minicrm.dashboard;

import com.minicrm.common.ApiException;
import com.minicrm.common.BusinessRules;
import com.minicrm.common.PageSupport;
import com.minicrm.common.JdbcTimeUtils;
import com.minicrm.common.SecurityUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardService {
  private final JdbcTemplate jdbc;

  public DashboardService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Filter normalized(SecurityUser actor, Filter filter) {
    if (BusinessRules.isSales(actor)) {
      if (filter.ownerId() != null && !actor.id().equals(filter.ownerId())) {
        throw new ApiException("FORBIDDEN", "销售只能查看自己的数据", HttpStatus.FORBIDDEN);
      }
      return new Filter(filter.from(), filter.to(), actor.id(), filter.source());
    }
    if (filter.ownerId() != null) {
      ensureUser(filter.ownerId(), actor.organizationId());
    }
    return filter;
  }

  public Map<String, Object> summary(SecurityUser actor, Filter raw) {
    Filter filter = normalized(actor, raw);
    String leadWhere = leadWhere(filter, "l");
    List<Object> leadArgs = leadArgs(actor, filter);

    Long total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM leads l JOIN pipeline_stages s ON s.id = l.stage_id "
            + "WHERE l.organization_id = ? AND l.archived_at IS NULL" + leadWhere,
        Long.class,
        leadArgs.toArray());
    Long won = jdbc.queryForObject(
        "SELECT COUNT(*) FROM leads l JOIN pipeline_stages s ON s.id = l.stage_id "
            + "WHERE l.organization_id = ? AND l.archived_at IS NULL AND s.is_won = true" + leadWhere,
        Long.class,
        leadArgs.toArray());
    Long lost = jdbc.queryForObject(
        "SELECT COUNT(*) FROM leads l JOIN pipeline_stages s ON s.id = l.stage_id "
            + "WHERE l.organization_id = ? AND l.archived_at IS NULL AND s.is_lost = true" + leadWhere,
        Long.class,
        leadArgs.toArray());
    Long overdue = jdbc.queryForObject(
        "SELECT COUNT(*) FROM tasks t WHERE t.organization_id = ? "
            + "AND t.status = 'pending'::task_status AND t.due_at < now()"
            + taskScope(filter),
        Long.class,
        taskArgs(actor, filter).toArray());
    Long activityCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM activity_logs a "
            + "LEFT JOIN leads l ON l.id = a.resource_id AND a.resource_type = 'LEAD' "
            + "WHERE a.organization_id = ?"
            + dateWhere(filter, "a.created_at")
            + activityOwnerWhere(filter),
        Long.class,
        activityArgs(actor, filter).toArray());

    long totalValue = total == null ? 0 : total;
    long wonValue = won == null ? 0 : won;
    long lostValue = lost == null ? 0 : lost;
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalLeads", totalValue);
    result.put("openLeads", Math.max(0, totalValue - wonValue - lostValue));
    result.put("wonLeads", wonValue);
    result.put("lostLeads", lostValue);
    result.put("overdueTasks", overdue == null ? 0 : overdue);
    result.put("conversionRate", totalValue == 0 ? 0 : (double) wonValue / totalValue);
    result.put("newThisPeriod", newCount(actor, filter));
    result.put("activitiesThisPeriod", activityCount == null ? 0 : activityCount);
    return result;
  }

  public PageSupport.Result<Map<String, Object>> funnel(
      SecurityUser actor,
      Filter raw,
      Integer page,
      Integer pageSize) {
    Filter filter = normalized(actor, raw);
    PageSupport.PageRequest paging = PageSupport.request(page, pageSize);
    String where = leadWhere(filter, "l");
    List<Object> filters = leadArgs(actor, filter);

    Long total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM pipeline_stages WHERE organization_id = ? AND is_active = true",
        Long.class,
        actor.organizationId());

    List<Object> args = new ArrayList<>();
    args.add(actor.organizationId());
    args.addAll(filters.subList(1, filters.size()));
    args.add(actor.organizationId());
    args.add(paging.pageSize());
    args.add(paging.offset());
    List<Map<String, Object>> items = jdbc.query(
        "SELECT s.id AS stage_id, s.name AS stage_name, s.color, COUNT(l.id) AS count "
            + "FROM pipeline_stages s "
            + "LEFT JOIN leads l ON l.stage_id = s.id "
            + "AND l.organization_id = ? AND l.archived_at IS NULL"
            + where
            + " WHERE s.organization_id = ? AND s.is_active = true "
            + "GROUP BY s.id ORDER BY s.sort_order LIMIT ? OFFSET ?",
        (rs, row) -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("stageId", rs.getObject("stage_id"));
          item.put("stageName", rs.getString("stage_name"));
          item.put("color", rs.getString("color"));
          item.put("count", rs.getLong("count"));
          return item;
        },
        args.toArray());
    return new PageSupport.Result<>(items, PageSupport.meta(paging, total == null ? 0 : total));
  }

  public PageSupport.Result<Map<String, Object>> sources(
      SecurityUser actor,
      Filter raw,
      Integer page,
      Integer pageSize) {
    Filter filter = normalized(actor, raw);
    PageSupport.PageRequest paging = PageSupport.request(page, pageSize);
    String where = leadWhere(filter, "l");
    List<Object> args = leadArgs(actor, filter);
    Long total = jdbc.queryForObject(
        "SELECT COUNT(DISTINCT l.source) FROM leads l "
            + "WHERE l.organization_id = ? AND l.archived_at IS NULL" + where,
        Long.class,
        args.toArray());
    List<Object> listArgs = new ArrayList<>(args);
    listArgs.add(paging.pageSize());
    listArgs.add(paging.offset());
    List<Map<String, Object>> items = jdbc.query(
        "SELECT l.source, COUNT(*) AS count, COUNT(*) FILTER (WHERE s.is_won) AS won "
            + "FROM leads l JOIN pipeline_stages s ON s.id = l.stage_id "
            + "WHERE l.organization_id = ? AND l.archived_at IS NULL" + where
            + " GROUP BY l.source ORDER BY count DESC LIMIT ? OFFSET ?",
        (rs, row) -> {
          long count = rs.getLong("count");
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("source", rs.getString("source"));
          item.put("count", count);
          item.put("won", rs.getLong("won"));
          item.put("conversionRate", count == 0 ? 0 : rs.getLong("won") / (double) count);
          return item;
        },
        listArgs.toArray());
    return new PageSupport.Result<>(items, PageSupport.meta(paging, total == null ? 0 : total));
  }

  public PageSupport.Result<Map<String, Object>> owners(
      SecurityUser actor,
      Filter raw,
      Integer page,
      Integer pageSize) {
    Filter filter = normalized(actor, raw);
    PageSupport.PageRequest paging = PageSupport.request(page, pageSize);
    String where = leadWhere(filter, "l");
    List<Object> args = leadArgs(actor, filter);
    Long total = jdbc.queryForObject(
        "SELECT COUNT(DISTINCT l.owner_id) FROM leads l "
            + "WHERE l.organization_id = ? AND l.archived_at IS NULL" + where,
        Long.class,
        args.toArray());
    List<Object> listArgs = new ArrayList<>(args);
    listArgs.add(paging.pageSize());
    listArgs.add(paging.offset());
    List<Map<String, Object>> items = jdbc.query(
        "SELECT l.owner_id, u.name AS owner_name, COUNT(*) AS total, "
            + "COUNT(*) FILTER (WHERE s.is_won) AS won, "
            + "(SELECT COUNT(*) FROM tasks t WHERE t.assignee_id = l.owner_id "
            + "AND t.organization_id = l.organization_id "
            + "AND t.status = 'pending'::task_status AND t.due_at < now()) AS overdue_tasks "
            + "FROM leads l LEFT JOIN users u ON u.id = l.owner_id "
            + "JOIN pipeline_stages s ON s.id = l.stage_id "
            + "WHERE l.organization_id = ? AND l.archived_at IS NULL" + where
            + " GROUP BY l.owner_id, u.name, l.organization_id "
            + "ORDER BY total DESC LIMIT ? OFFSET ?",
        (rs, row) -> {
          long count = rs.getLong("total");
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("ownerId", rs.getObject("owner_id"));
          item.put("ownerName", rs.getString("owner_name"));
          item.put("total", count);
          item.put("won", rs.getLong("won"));
          item.put("overdueTasks", rs.getLong("overdue_tasks"));
          item.put("conversionRate", count == 0 ? 0 : rs.getLong("won") / (double) count);
          return item;
        },
        listArgs.toArray());
    return new PageSupport.Result<>(items, PageSupport.meta(paging, total == null ? 0 : total));
  }

  public PageSupport.Result<Map<String, Object>> activities(
      SecurityUser actor,
      Filter raw,
      Integer page,
      Integer pageSize) {
    Filter filter = normalized(actor, raw);
    PageSupport.PageRequest paging = PageSupport.request(page, pageSize);
    String scope = activityOwnerWhere(filter);
    List<Object> args = activityArgs(actor, filter);
    Long total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM activity_logs a "
            + "LEFT JOIN leads l ON l.id = a.resource_id AND a.resource_type = 'LEAD' "
            + "WHERE a.organization_id = ?"
            + dateWhere(filter, "a.created_at")
            + scope,
        Long.class,
        args.toArray());
    List<Object> listArgs = new ArrayList<>(args);
    listArgs.add(paging.pageSize());
    listArgs.add(paging.offset());
    List<Map<String, Object>> items = jdbc.query(
        "SELECT a.id, a.action, a.resource_id, "
            + "COALESCE(l.name, l.company, '') AS lead_name, "
            + "u.name AS actor_name, a.created_at "
            + "FROM activity_logs a "
            + "LEFT JOIN leads l ON l.id = a.resource_id AND a.resource_type = 'LEAD' "
            + "LEFT JOIN users u ON u.id = a.actor_id "
            + "WHERE a.organization_id = ?"
            + dateWhere(filter, "a.created_at")
            + scope
            + " ORDER BY a.created_at DESC LIMIT ? OFFSET ?",
        this::activity,
        listArgs.toArray());
    return new PageSupport.Result<>(items, PageSupport.meta(paging, total == null ? 0 : total));
  }

  private Map<String, Object> activity(ResultSet rs, int row) throws SQLException {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", rs.getObject("id"));
    item.put("type", rs.getString("action"));
    item.put("leadId", rs.getObject("resource_id"));
    item.put("leadName", rs.getString("lead_name"));
    item.put("title", rs.getString("action"));
    item.put("actorName", rs.getString("actor_name"));
    item.put("occurredAt", JdbcTimeUtils.fromDbTime(rs, "created_at"));
    return item;
  }

  private long newCount(SecurityUser actor, Filter filter) {
    String where = leadWhere(filter, "l");
    Long value = jdbc.queryForObject(
        "SELECT COUNT(*) FROM leads l WHERE l.organization_id = ? AND l.archived_at IS NULL" + where,
        Long.class,
        leadArgs(actor, filter).toArray());
    return value == null ? 0 : value;
  }

  private String leadWhere(Filter filter, String alias) {
    StringBuilder sql = new StringBuilder();
    if (filter.ownerId() != null) {
      sql.append(" AND ").append(alias).append(".owner_id = ?");
    }
    if (filter.source() != null && !filter.source().isBlank()) {
      sql.append(" AND ").append(alias).append(".source = ?");
    }
    if (filter.from() != null) {
      sql.append(" AND ").append(alias).append(".created_at >= ?");
    }
    if (filter.to() != null) {
      sql.append(" AND ").append(alias).append(".created_at <= ?");
    }
    return sql.toString();
  }

  private List<Object> leadArgs(SecurityUser actor, Filter filter) {
    List<Object> args = new ArrayList<>();
    args.add(actor.organizationId());
    if (filter.ownerId() != null) args.add(filter.ownerId());
    if (filter.source() != null && !filter.source().isBlank()) args.add(filter.source());
    if (filter.from() != null) args.add(filter.from());
    if (filter.to() != null) args.add(filter.to());
    return args;
  }

  private String taskScope(Filter filter) {
    return filter.ownerId() == null ? "" : " AND t.assignee_id = ?";
  }

  private List<Object> taskArgs(SecurityUser actor, Filter filter) {
    List<Object> args = new ArrayList<>();
    args.add(actor.organizationId());
    if (filter.ownerId() != null) args.add(filter.ownerId());
    return args;
  }

  private String dateWhere(Filter filter, String column) {
    StringBuilder sql = new StringBuilder();
    if (filter.from() != null) sql.append(" AND ").append(column).append(" >= ?");
    if (filter.to() != null) sql.append(" AND ").append(column).append(" <= ?");
    return sql.toString();
  }

  private String activityOwnerWhere(Filter filter) {
    if (filter.ownerId() == null) {
      return "";
    }
    return """
        AND (
          (a.resource_type = 'LEAD' AND l.owner_id = ?)
          OR (a.resource_type = 'FOLLOW_UP' AND EXISTS (
            SELECT 1 FROM follow_ups f
            JOIN leads fl ON fl.id = f.lead_id
            WHERE f.id = a.resource_id AND fl.owner_id = ?
          ))
          OR (a.resource_type = 'TASK' AND EXISTS (
            SELECT 1 FROM tasks t_scope
            WHERE t_scope.id = a.resource_id AND t_scope.assignee_id = ?
          ))
        )
        """;
  }

  private List<Object> activityArgs(SecurityUser actor, Filter filter) {
    List<Object> args = new ArrayList<>();
    args.add(actor.organizationId());
    if (filter.from() != null) args.add(filter.from());
    if (filter.to() != null) args.add(filter.to());
    if (filter.ownerId() != null) {
      args.add(filter.ownerId());
      args.add(filter.ownerId());
      args.add(filter.ownerId());
    }
    return args;
  }

  private void ensureUser(UUID id, UUID organizationId) {
    if (id == null) {
      return;
    }
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM users WHERE id = ? AND organization_id = ?",
        Integer.class,
        id,
        organizationId);
    if (count == null || count != 1) {
      throw new ApiException("RESOURCE_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
    }
  }

  public record Filter(String from, String to, UUID ownerId, String source) {}
}
