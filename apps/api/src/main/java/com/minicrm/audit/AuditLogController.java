package com.minicrm.audit;

import com.minicrm.common.ApiEnvelope;
import com.minicrm.common.BusinessRules;
import com.minicrm.common.CurrentUser;
import com.minicrm.common.PageSupport;
import com.minicrm.common.RoleCode;
import com.minicrm.common.Roles;
import com.minicrm.common.SecurityUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/audit-logs", "/api/v1/audit-logs"})
@Roles({RoleCode.OWNER, RoleCode.ADMIN})
public class AuditLogController {
  private final JdbcTemplate jdbc;
  public AuditLogController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @GetMapping
  public ApiEnvelope<List<Map<String, Object>>> list(Authentication authentication, @RequestParam(required = false) UUID actorId, @RequestParam(required = false) String action, @RequestParam(required = false) String resourceType, @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
    SecurityUser actor = CurrentUser.require(authentication); PageSupport.PageRequest paging = PageSupport.request(page, pageSize); StringBuilder where = new StringBuilder(" WHERE a.organization_id = ? "); List<Object> args = new ArrayList<>(); args.add(actor.organizationId()); if (actorId != null) { where.append(" AND a.actor_id = ?"); args.add(actorId); } if (action != null) { where.append(" AND a.action = ?"); args.add(action); } if (resourceType != null) { where.append(" AND a.resource_type = ?"); args.add(resourceType); } if (from != null) { where.append(" AND a.created_at >= ?"); args.add(from); } if (to != null) { where.append(" AND a.created_at <= ?"); args.add(to); } Long total = jdbc.queryForObject("SELECT COUNT(*) FROM activity_logs a" + where, Long.class, args.toArray()); List<Object> listArgs = new ArrayList<>(args); listArgs.add(paging.pageSize()); listArgs.add(paging.offset()); List<Map<String, Object>> items = jdbc.query("SELECT a.id, a.actor_id, u.name AS actor_name, a.action, a.resource_type, a.resource_id, a.request_id, a.metadata, a.created_at FROM activity_logs a LEFT JOIN users u ON u.id = a.actor_id" + where + " ORDER BY a.created_at DESC LIMIT ? OFFSET ?", (rs, row) -> row(rs), listArgs.toArray()); return ApiEnvelope.ok(items, PageSupport.meta(paging, total == null ? 0 : total));
  }

  private Map<String, Object> row(ResultSet rs) throws SQLException { Map<String, Object> result = new LinkedHashMap<>(); result.put("id", rs.getObject("id")); result.put("actorId", rs.getObject("actor_id")); result.put("actorName", rs.getString("actor_name")); result.put("action", rs.getString("action")); result.put("resourceType", rs.getString("resource_type")); result.put("resourceId", rs.getObject("resource_id")); result.put("requestId", rs.getString("request_id")); result.put("metadata", rs.getObject("metadata")); result.put("createdAt", rs.getObject("created_at")); return result; }
}
