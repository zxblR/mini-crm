package com.minicrm.pipeline;

import com.minicrm.common.ApiException;
import com.minicrm.common.BusinessRules;
import com.minicrm.common.PageSupport;
import com.minicrm.common.SecurityUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Deprecated(forRemoval = false)
@Service
public class PipelineStageService {
  private final JdbcTemplate jdbc;

  public PipelineStageService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PageSupport.Result<Map<String, Object>> list(SecurityUser actor, Integer page, Integer pageSize, Boolean isActive) {
    PageSupport.PageRequest paging = PageSupport.request(page, pageSize);
    String activeClause = isActive == null ? "" : " AND is_active = ?";
    List<Object> args = new ArrayList<>();
    args.add(actor.organizationId());
    if (isActive != null) args.add(isActive);
    args.add(paging.pageSize());
    args.add(paging.offset());
    Long total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM pipeline_stages WHERE organization_id = ?" + activeClause,
        Long.class, args.subList(0, args.size() - 2).toArray());
    List<Map<String, Object>> items = jdbc.query(
        "SELECT id, code, name, color, sort_order, is_default, is_won, is_lost, is_active, created_at, updated_at "
            + "FROM pipeline_stages WHERE organization_id = ?" + activeClause
            + " ORDER BY sort_order LIMIT ? OFFSET ?",
        (rs, row) -> stage(rs), args.toArray());
    return new PageSupport.Result<>(items, PageSupport.meta(paging, total == null ? 0 : total));
  }

  public Map<String, Object> get(SecurityUser actor, UUID id) {
    return jdbc.query("SELECT id, code, name, color, sort_order, is_default, is_won, is_lost, is_active, created_at, updated_at "
            + "FROM pipeline_stages WHERE id = ? AND organization_id = ?",
        rs -> rs.next() ? stage(rs) : null, id, actor.organizationId());
  }

  @Transactional
  public Map<String, Object> create(SecurityUser actor, CreateRequest request) {
    BusinessRules.requireAdmin(actor);
    if (request.isWon() && request.isLost()) {
      throw conflict("阶段不能同时标记为赢单和输单");
    }
    UUID id = UUID.randomUUID();
    try {
      if (Boolean.TRUE.equals(request.isDefault())) {
        jdbc.update("UPDATE pipeline_stages SET is_default = false, updated_at = now() WHERE organization_id = ?", actor.organizationId());
      }
      jdbc.update("""
          INSERT INTO pipeline_stages (id, organization_id, code, name, color, sort_order, is_default, is_won, is_lost)
          VALUES (?, ?, ?, ?, COALESCE(?, '#64748B'), ?, ?, ?, ?)
          """, id, actor.organizationId(), request.code().trim(), request.name().trim(), request.color(),
          request.sortOrder(), Boolean.TRUE.equals(request.isDefault()), Boolean.TRUE.equals(request.isWon()), Boolean.TRUE.equals(request.isLost()));
    } catch (org.springframework.dao.DuplicateKeyException exception) {
      throw conflict("阶段代码或顺序已存在");
    }
    return get(actor, id);
  }

  @Transactional
  public Map<String, Object> update(SecurityUser actor, UUID id, UpdateRequest request) {
    BusinessRules.requireAdmin(actor);
    Map<String, Object> current = get(actor, id);
    if (current == null) throw notFound();
    boolean isWon = request.isWon() == null ? (boolean) current.get("isWon") : request.isWon();
    boolean isLost = request.isLost() == null ? (boolean) current.get("isLost") : request.isLost();
    if (isWon && isLost) {
      throw conflict("阶段不能同时标记为赢单和输单");
    }
    if (Boolean.TRUE.equals(request.isDefault())) {
      jdbc.update("UPDATE pipeline_stages SET is_default = false, updated_at = now() WHERE organization_id = ?", actor.organizationId());
    }
    try {
      jdbc.update("""
          UPDATE pipeline_stages SET
            name = COALESCE(?, name), color = COALESCE(?, color), sort_order = COALESCE(?, sort_order),
            is_default = COALESCE(?, is_default), is_won = COALESCE(?, is_won), is_lost = COALESCE(?, is_lost),
            is_active = COALESCE(?, is_active), updated_at = now()
          WHERE id = ? AND organization_id = ?
          """, request.name(), request.color(), request.sortOrder(), request.isDefault(), request.isWon(), request.isLost(),
          request.isActive(), id, actor.organizationId());
    } catch (org.springframework.dao.DuplicateKeyException exception) {
      throw conflict("阶段顺序已存在");
    }
    return get(actor, id);
  }

  @Transactional
  public List<Map<String, Object>> reorder(SecurityUser actor, List<UUID> stageIds) {
    BusinessRules.requireAdmin(actor);
    if (stageIds == null || stageIds.isEmpty() || stageIds.stream().distinct().count() != stageIds.size()) {
      throw new ApiException("VALIDATION_FAILED", "阶段列表无效", HttpStatus.BAD_REQUEST);
    }
    Long activeCount = jdbc.queryForObject("SELECT COUNT(*) FROM pipeline_stages WHERE organization_id = ? AND is_active = true", Long.class, actor.organizationId());
    if (activeCount == null || activeCount != stageIds.size()) {
      throw new ApiException("VALIDATION_FAILED", "必须包含当前组织全部活动阶段", HttpStatus.BAD_REQUEST);
    }
    for (UUID id : stageIds) {
      Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pipeline_stages WHERE id = ? AND organization_id = ? AND is_active = true", Integer.class, id, actor.organizationId());
      if (count == null || count != 1) throw notFound();
    }
    jdbc.update("UPDATE pipeline_stages SET sort_order = sort_order + 10000, updated_at = now() WHERE organization_id = ?", actor.organizationId());
    for (int i = 0; i < stageIds.size(); i++) {
      jdbc.update("UPDATE pipeline_stages SET sort_order = ?, updated_at = now() WHERE id = ? AND organization_id = ?", i + 1, stageIds.get(i), actor.organizationId());
    }
    return jdbc.query("SELECT id, code, name, color, sort_order, is_default, is_won, is_lost, is_active, created_at, updated_at "
            + "FROM pipeline_stages WHERE organization_id = ? ORDER BY sort_order", (rs, row) -> stage(rs), actor.organizationId());
  }

  private Map<String, Object> stage(ResultSet rs) throws SQLException {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", rs.getObject("id"));
    result.put("code", rs.getString("code"));
    result.put("name", rs.getString("name"));
    result.put("color", rs.getString("color"));
    result.put("sortOrder", rs.getInt("sort_order"));
    result.put("isDefault", rs.getBoolean("is_default"));
    result.put("isWon", rs.getBoolean("is_won"));
    result.put("isLost", rs.getBoolean("is_lost"));
    result.put("isActive", rs.getBoolean("is_active"));
    result.put("createdAt", rs.getObject("created_at"));
    result.put("updatedAt", rs.getObject("updated_at"));
    return result;
  }

  private ApiException notFound() { return new ApiException("RESOURCE_NOT_FOUND", "阶段不存在", HttpStatus.NOT_FOUND); }
  private ApiException conflict(String message) { return new ApiException("STAGE_CONFLICT", message, HttpStatus.CONFLICT); }

  public record CreateRequest(
      @NotBlank @Size(max = 40) String code,
      @NotBlank @Size(max = 60) String name,
      @Size(max = 7) String color,
      @NotNull @Positive Integer sortOrder,
      Boolean isDefault,
      Boolean isWon,
      Boolean isLost) {
    public CreateRequest { if (isDefault == null) isDefault = false; if (isWon == null) isWon = false; if (isLost == null) isLost = false; }
  }

  public record UpdateRequest(
      @Size(max = 60) String name,
      @Size(max = 7) String color,
      @Positive Integer sortOrder,
      Boolean isDefault,
      Boolean isWon,
      Boolean isLost,
      Boolean isActive) {}
}
