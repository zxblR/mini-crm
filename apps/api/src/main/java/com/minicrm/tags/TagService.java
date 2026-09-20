package com.minicrm.tags;

import com.minicrm.common.ApiException;
import com.minicrm.common.BusinessRules;
import com.minicrm.common.PageSupport;
import com.minicrm.common.SecurityUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
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
public class TagService {
  private final JdbcTemplate jdbc;

  public TagService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public PageSupport.Result<Map<String, Object>> list(SecurityUser actor, Integer page, Integer pageSize, String keyword) {
    PageSupport.PageRequest paging = PageSupport.request(page, pageSize);
    String filter = keyword == null || keyword.isBlank() ? "" : " AND LOWER(name) LIKE ?";
    List<Object> args = new ArrayList<>(); args.add(actor.organizationId()); if (!filter.isEmpty()) args.add("%" + keyword.trim().toLowerCase() + "%");
    Long total = jdbc.queryForObject("SELECT COUNT(*) FROM tags WHERE organization_id = ?" + filter, Long.class, args.toArray());
    args.add(paging.pageSize()); args.add(paging.offset());
    List<Map<String, Object>> items = jdbc.query("SELECT id, name, color, created_at, updated_at FROM tags WHERE organization_id = ?" + filter + " ORDER BY name LIMIT ? OFFSET ?", (rs, row) -> tag(rs), args.toArray());
    return new PageSupport.Result<>(items, PageSupport.meta(paging, total == null ? 0 : total));
  }

  public Map<String, Object> create(SecurityUser actor, Request request) {
    BusinessRules.requireAdmin(actor);
    UUID id = UUID.randomUUID();
    try {
      jdbc.update("INSERT INTO tags (id, organization_id, name, normalized_name, color) VALUES (?, ?, ?, ?, COALESCE(?, '#64748B'))", id, actor.organizationId(), request.name().trim(), request.name().trim().toLowerCase(), request.color());
    } catch (DuplicateKeyException exception) { throw new ApiException("STAGE_CONFLICT", "标签名称已存在", HttpStatus.CONFLICT); }
    return get(actor, id);
  }

  public Map<String, Object> update(SecurityUser actor, UUID id, Request request) {
    BusinessRules.requireAdmin(actor);
    if (get(actor, id) == null) throw notFound();
    try {
      jdbc.update("UPDATE tags SET name = COALESCE(?, name), normalized_name = COALESCE(?, normalized_name), color = COALESCE(?, color), updated_at = now() WHERE id = ? AND organization_id = ?", request.name(), request.name() == null ? null : request.name().trim().toLowerCase(), request.color(), id, actor.organizationId());
    } catch (DuplicateKeyException exception) { throw new ApiException("STAGE_CONFLICT", "标签名称已存在", HttpStatus.CONFLICT); }
    return get(actor, id);
  }

  public Map<String, Object> get(SecurityUser actor, UUID id) {
    return jdbc.query("SELECT id, name, color, created_at, updated_at FROM tags WHERE id = ? AND organization_id = ?", rs -> rs.next() ? tag(rs) : null, id, actor.organizationId());
  }

  private Map<String, Object> tag(ResultSet rs) throws SQLException {
    Map<String, Object> result = new LinkedHashMap<>(); result.put("id", rs.getObject("id")); result.put("name", rs.getString("name")); result.put("color", rs.getString("color")); result.put("createdAt", rs.getObject("created_at")); result.put("updatedAt", rs.getObject("updated_at")); return result;
  }
  private ApiException notFound() { return new ApiException("RESOURCE_NOT_FOUND", "标签不存在", HttpStatus.NOT_FOUND); }
  public record Request(@NotBlank @Size(max = 40) String name, @Size(max = 7) String color) {}
}
