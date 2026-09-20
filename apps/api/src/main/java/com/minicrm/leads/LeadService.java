package com.minicrm.leads;

import com.minicrm.common.ActivityLogService;
import com.minicrm.common.ApiException;
import com.minicrm.common.BusinessRules;
import com.minicrm.common.PageSupport;
import com.minicrm.common.SecurityUser;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LeadService {
  private final JdbcTemplate jdbc;
  private final ActivityLogService activityLogService;

  public LeadService(JdbcTemplate jdbc, ActivityLogService activityLogService) {
    this.jdbc = jdbc;
    this.activityLogService = activityLogService;
  }

  public PageSupport.Result<Map<String, Object>> list(SecurityUser actor, Query query) {
    if (query.ownerId() != null && BusinessRules.isSales(actor) && !query.ownerId().equals(actor.id())) {
      throw new ApiException("FORBIDDEN", "销售只能查看自己负责的线索", HttpStatus.FORBIDDEN);
    }
    ensureOrgReference(query.ownerId(), actor.organizationId(), "用户不存在");
    ensureOrgReference(query.stageId(), actor.organizationId(), "阶段不存在", "pipeline_stages");
    PageSupport.PageRequest paging = PageSupport.request(query.page(), query.pageSize());
    StringBuilder where = new StringBuilder(" WHERE l.organization_id = ? ");
    List<Object> args = new ArrayList<>();
    args.add(actor.organizationId());
    if (BusinessRules.isSales(actor)) { where.append(" AND l.owner_id = ? "); args.add(actor.id()); }
    if (query.keyword() != null && !query.keyword().isBlank()) {
      where.append(" AND (LOWER(COALESCE(l.name, '')) LIKE ? OR LOWER(COALESCE(l.company, '')) LIKE ? OR LOWER(COALESCE(l.email, '')) LIKE ? OR COALESCE(l.phone, '') LIKE ?) ");
      String keyword = "%" + query.keyword().trim().toLowerCase() + "%";
      args.add(keyword); args.add(keyword); args.add(keyword); args.add(keyword);
    }
    if (query.stageId() != null) { where.append(" AND l.stage_id = ? "); args.add(query.stageId()); }
    if (query.ownerId() != null) { where.append(" AND l.owner_id = ? "); args.add(query.ownerId()); }
    if (query.source() != null && !query.source().isBlank()) { where.append(" AND l.source = ? "); args.add(query.source().trim()); }
    if (query.from() != null) { where.append(" AND l.created_at >= ? "); args.add(query.from()); }
    if (query.to() != null) { where.append(" AND l.created_at <= ? "); args.add(query.to()); }
    if (Boolean.TRUE.equals(query.archived())) where.append(" AND l.archived_at IS NOT NULL ");
    else where.append(" AND l.archived_at IS NULL ");

    String countSql = "SELECT COUNT(*) FROM leads l" + where;
    Long total = jdbc.queryForObject(countSql, Long.class, args.toArray());
    String sort = switch (query.sortBy() == null ? "updatedAt" : query.sortBy()) {
      case "createdAt" -> "l.created_at";
      case "nextFollowUpAt" -> "l.next_follow_up_at";
      case "company" -> "l.company";
      default -> "l.updated_at";
    };
    String order = "desc".equalsIgnoreCase(query.sortOrder()) ? "DESC" : "ASC";
    List<Object> listArgs = new ArrayList<>(args);
    listArgs.add(paging.pageSize()); listArgs.add(paging.offset());
    List<Map<String, Object>> items = jdbc.query("""
        SELECT l.id, l.name, l.company, l.phone, l.email, l.source, l.industry, l.region,
               l.next_follow_up_at, l.archived_at, l.created_at, l.updated_at, l.owner_id,
               s.id AS stage_id, s.code AS stage_code, s.name AS stage_name, s.color AS stage_color,
               s.sort_order, s.is_default, s.is_won, s.is_lost, s.is_active,
               o.name AS owner_name, o.email AS owner_email
        FROM leads l JOIN pipeline_stages s ON s.id = l.stage_id
        LEFT JOIN users o ON o.id = l.owner_id
        """ + where + " ORDER BY " + sort + " " + order + " NULLS LAST LIMIT ? OFFSET ?",
        (rs, row) -> summary(rs, false), listArgs.toArray());
    for (Map<String, Object> item : items) item.put("tags", tags(actor.organizationId(), (UUID) item.get("id")));
    return new PageSupport.Result<>(items, PageSupport.meta(paging, total == null ? 0 : total));
  }

  public Map<String, Object> get(SecurityUser actor, UUID id) {
    Map<String, Object> lead = jdbc.query("""
        SELECT l.id, l.organization_id, l.name, l.company, l.phone, l.email, l.source, l.industry, l.region,
               l.notes, l.next_follow_up_at, l.archived_at, l.created_at, l.updated_at,
               l.owner_id, l.created_by_id, s.id AS stage_id, s.code AS stage_code, s.name AS stage_name,
               s.color AS stage_color, s.sort_order, s.is_default, s.is_won, s.is_lost, s.is_active,
               o.name AS owner_name, o.email AS owner_email, c.name AS creator_name, c.email AS creator_email
        FROM leads l JOIN pipeline_stages s ON s.id = l.stage_id
        LEFT JOIN users o ON o.id = l.owner_id JOIN users c ON c.id = l.created_by_id
        WHERE l.id = ? AND l.organization_id = ?
        """, rs -> rs.next() ? summary(rs, true) : null, id, actor.organizationId());
    if (lead == null || (BusinessRules.isSales(actor) && !actor.id().equals(lead.get("ownerId")))) return notFound();
    UUID leadId = (UUID) lead.get("id");
    lead.put("tags", tags(actor.organizationId(), leadId));
    lead.put("timeline", timeline(actor.organizationId(), leadId));
    return lead;
  }

  @Transactional
  public Map<String, Object> create(SecurityUser actor, CreateRequest request, HttpServletRequest httpRequest) {
    if (blank(request.name()) == null && blank(request.company()) == null) {
      throw new ApiException("VALIDATION_FAILED", "姓名或公司至少填写一个", HttpStatus.BAD_REQUEST);
    }
    UUID stageId = request.stageId() == null ? defaultStage(actor.organizationId()) : request.stageId();
    ensureOrgReference(stageId, actor.organizationId(), "阶段不存在");
    UUID ownerId = request.ownerId();
    if (BusinessRules.isSales(actor)) {
      if (ownerId != null && !ownerId.equals(actor.id())) throw new ApiException("FORBIDDEN", "销售只能将线索分配给自己", HttpStatus.FORBIDDEN);
      ownerId = actor.id();
    } else if (ownerId != null) {
      ensureOrgReference(ownerId, actor.organizationId(), "负责人不存在");
    }
    checkDuplicate(actor.organizationId(), request.email(), request.phone(), null);
    List<UUID> tagIds = validateTags(actor.organizationId(), request.tagIds());
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO leads (id, organization_id, name, company, phone, normalized_phone, email, normalized_email,
          source, industry, region, notes, stage_id, owner_id, created_by_id, next_follow_up_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, id, actor.organizationId(), blank(request.name()), blank(request.company()), blank(request.phone()), normalizePhone(request.phone()),
        blank(request.email()), normalizeEmail(request.email()), request.source().trim(), blank(request.industry()), blank(request.region()),
        blank(request.notes()), stageId, ownerId, actor.id(), request.nextFollowUpAt());
    replaceTags(id, tagIds);
    activityLogService.record(actor, "CREATE_LEAD", "LEAD", id, null, httpRequest);
    return get(actor, id);
  }

  @Transactional
  public Map<String, Object> update(SecurityUser actor, UUID id, UpdateRequest request, HttpServletRequest httpRequest) {
    Map<String, Object> current = get(actor, id);
    UUID ownerId = (UUID) current.get("ownerId");
    BusinessRules.requireLeadOwnerOrAdmin(actor, ownerId);
    if (!BusinessRules.isAdmin(actor) && request.ownerId() != null) throw new ApiException("FORBIDDEN", "销售不能修改负责人", HttpStatus.FORBIDDEN);
    UUID targetOwner = request.ownerId();
    if (targetOwner != null) ensureOrgReference(targetOwner, actor.organizationId(), "负责人不存在");
    checkDuplicate(actor.organizationId(), request.email(), request.phone(), id);
    List<UUID> tagIds = request.tagIds() == null ? null : validateTags(actor.organizationId(), request.tagIds());
    jdbc.update("""
        UPDATE leads SET name = COALESCE(?, name), company = COALESCE(?, company), phone = COALESCE(?, phone),
          normalized_phone = COALESCE(?, normalized_phone), email = COALESCE(?, email), normalized_email = COALESCE(?, normalized_email),
          source = COALESCE(?, source), industry = COALESCE(?, industry), region = COALESCE(?, region), notes = COALESCE(?, notes),
          owner_id = COALESCE(?, owner_id), next_follow_up_at = COALESCE(?, next_follow_up_at), updated_at = now()
        WHERE id = ? AND organization_id = ?
        """, blank(request.name()), blank(request.company()), blank(request.phone()), normalizePhone(request.phone()), blank(request.email()),
        normalizeEmail(request.email()), blank(request.source()), blank(request.industry()), blank(request.region()), blank(request.notes()),
        targetOwner, request.nextFollowUpAt(), id, actor.organizationId());
    if (tagIds != null) replaceTags(id, tagIds);
    activityLogService.record(actor, "UPDATE_LEAD", "LEAD", id, null, httpRequest);
    return get(actor, id);
  }

  public Map<String, Object> archive(SecurityUser actor, UUID id, boolean restore, HttpServletRequest httpRequest) {
    Map<String, Object> current = get(actor, id);
    BusinessRules.requireLeadOwnerOrAdmin(actor, (UUID) current.get("ownerId"));
    if (restore && !BusinessRules.isAdmin(actor)) throw new ApiException("FORBIDDEN", "只有管理员可以恢复线索", HttpStatus.FORBIDDEN);
    jdbc.update("UPDATE leads SET archived_at = " + (restore ? "NULL" : "now()") + ", updated_at = now() WHERE id = ? AND organization_id = ?", id, actor.organizationId());
    activityLogService.record(actor, restore ? "RESTORE_LEAD" : "ARCHIVE_LEAD", "LEAD", id, null, httpRequest);
    return get(actor, id);
  }

  public Map<String, Object> assign(SecurityUser actor, UUID id, UUID ownerId, HttpServletRequest request) {
    BusinessRules.requireAdmin(actor);
    get(actor, id); ensureOrgReference(ownerId, actor.organizationId(), "负责人不存在");
    jdbc.update("UPDATE leads SET owner_id = ?, updated_at = now() WHERE id = ? AND organization_id = ?", ownerId, id, actor.organizationId());
    activityLogService.record(actor, "ASSIGN_LEAD", "LEAD", id, Map.of("ownerId", ownerId.toString()), request);
    return get(actor, id);
  }

  @Transactional
  public Map<String, Object> batchAssign(SecurityUser actor, BatchAssignRequest request, HttpServletRequest httpRequest) {
    BusinessRules.requireAdmin(actor);
    if (request.leadIds() == null || request.leadIds().isEmpty() || request.leadIds().size() > 100
        || request.leadIds().stream().distinct().count() != request.leadIds().size()) {
      throw new ApiException("VALIDATION_FAILED", "线索列表无效", HttpStatus.BAD_REQUEST);
    }
    ensureOrgReference(request.ownerId(), actor.organizationId(), "负责人不存在");
    for (UUID id : request.leadIds()) get(actor, id);
    for (UUID id : request.leadIds()) jdbc.update("UPDATE leads SET owner_id = ?, updated_at = now() WHERE id = ? AND organization_id = ?", request.ownerId(), id, actor.organizationId());
    activityLogService.record(actor, "BATCH_ASSIGN_LEAD", "LEAD", null, Map.of("ownerId", request.ownerId().toString(), "count", request.leadIds().size()), httpRequest);
    return Map.of("updated", request.leadIds().size(), "leadIds", request.leadIds());
  }

  @Transactional
  public Map<String, Object> changeStage(SecurityUser actor, UUID id, ChangeStageRequest request, HttpServletRequest httpRequest) {
    Map<String, Object> current = get(actor, id);
    BusinessRules.requireLeadOwnerOrAdmin(actor, (UUID) current.get("ownerId"));
    Map<String, Object> stage = jdbc.query("SELECT id, is_won, is_lost FROM pipeline_stages WHERE id = ? AND organization_id = ?", rs -> rs.next() ? Map.of("id", rs.getObject("id"), "isWon", rs.getBoolean("is_won"), "isLost", rs.getBoolean("is_lost")) : null, request.stageId(), actor.organizationId());
    if (stage == null) throw new ApiException("RESOURCE_NOT_FOUND", "阶段不存在", HttpStatus.NOT_FOUND);
    boolean isWon = (boolean) stage.get("isWon"); boolean isLost = (boolean) stage.get("isLost");
    if ((isWon || isLost) && blank(request.outcomeNote()) == null) throw new ApiException("OUTCOME_NOTE_REQUIRED", "进入终态必须填写结果说明", HttpStatus.BAD_REQUEST);
    if (isLost && blank(request.lostReason()) == null) throw new ApiException("LOST_REASON_REQUIRED", "进入输单阶段必须填写输单原因", HttpStatus.BAD_REQUEST);
    UUID oldStage = (UUID) ((Map<?, ?>) current.get("stage")).get("id");
    if (oldStage.equals(request.stageId())) throw new ApiException("STAGE_INVALID_TRANSITION", "不能重复进入当前阶段", HttpStatus.CONFLICT);
    jdbc.update("UPDATE leads SET stage_id = ?, won_at = ?, lost_at = ?, updated_at = now() WHERE id = ? AND organization_id = ?",
        request.stageId(), isWon ? java.sql.Timestamp.from(Instant.now()) : null, isLost ? java.sql.Timestamp.from(Instant.now()) : null, id, actor.organizationId());
    UUID historyId = UUID.randomUUID();
    jdbc.update("INSERT INTO stage_history (id, lead_id, from_stage_id, to_stage_id, actor_id, note, outcome_note, lost_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        historyId, id, oldStage, request.stageId(), actor.id(), blank(request.note()), blank(request.outcomeNote()), blank(request.lostReason()));
    activityLogService.record(actor, "CHANGE_LEAD_STAGE", "LEAD", id, Map.of("stageId", request.stageId().toString()), httpRequest);
    return Map.of("lead", get(actor, id), "history", Map.of("id", historyId, "stageId", request.stageId(), "createdAt", Instant.now()));
  }

  private UUID defaultStage(UUID organizationId) {
    UUID stageId = jdbc.query(
        "SELECT id FROM pipeline_stages WHERE organization_id = ? AND is_active = true ORDER BY is_default DESC, sort_order LIMIT 1",
        rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
        organizationId);
    if (stageId == null) {
      throw new ApiException("STAGE_CONFLICT", "组织没有可用的活动阶段", HttpStatus.CONFLICT);
    }
    return stageId;
  }

  private void checkDuplicate(UUID organizationId, String email, String phone, UUID ignoredId) {
    String emailNormalized = normalizeEmail(email); String phoneNormalized = normalizePhone(phone);
    if (emailNormalized == null && phoneNormalized == null) return;
    String sql = "SELECT id FROM leads WHERE organization_id = ? AND ((? IS NOT NULL AND normalized_email = ?) OR (? IS NOT NULL AND normalized_phone = ?))" + (ignoredId == null ? "" : " AND id <> ?") + " LIMIT 10";
    List<Object> args = new ArrayList<>(List.of(organizationId, emailNormalized, emailNormalized, phoneNormalized, phoneNormalized));
    if (ignoredId != null) args.add(ignoredId);
    List<UUID> ids = jdbc.query(sql, (rs, row) -> rs.getObject("id", UUID.class), args.toArray());
    if (!ids.isEmpty()) throw new ApiException("LEAD_DUPLICATE", "组织内已存在相同手机号或邮箱的线索", Map.of("candidateLeadIds", ids), HttpStatus.CONFLICT);
  }

  private List<UUID> validateTags(UUID organizationId, List<UUID> tagIds) {
    if (tagIds == null) return List.of();
    if (tagIds.stream().distinct().count() != tagIds.size() || tagIds.size() > 20) throw new ApiException("VALIDATION_FAILED", "标签列表无效", HttpStatus.BAD_REQUEST);
    for (UUID id : tagIds) ensureOrgReference(id, organizationId, "标签不存在", "tags");
    return tagIds;
  }

  private void replaceTags(UUID leadId, List<UUID> tagIds) {
    jdbc.update("DELETE FROM lead_tags WHERE lead_id = ?", leadId);
    for (UUID tagId : tagIds) jdbc.update("INSERT INTO lead_tags (lead_id, tag_id) VALUES (?, ?)", leadId, tagId);
  }

  private List<Map<String, Object>> tags(UUID organizationId, UUID leadId) {
    return jdbc.query("SELECT t.id, t.name, t.color FROM tags t JOIN lead_tags lt ON lt.tag_id = t.id WHERE lt.lead_id = ? AND t.organization_id = ? ORDER BY t.name",
        (rs, row) -> Map.of("id", rs.getObject("id"), "name", rs.getString("name"), "color", rs.getString("color")), leadId, organizationId);
  }

  private List<Map<String, Object>> timeline(UUID organizationId, UUID leadId) {
    List<Map<String, Object>> result = new ArrayList<>();
    result.addAll(jdbc.query("SELECT h.id, 'stage_changed' AS type, '阶段变更' AS title, h.note AS description, u.name AS actor_name, h.created_at AS occurred_at FROM stage_history h JOIN leads l ON l.id = h.lead_id JOIN users u ON u.id = h.actor_id WHERE h.lead_id = ? AND l.organization_id = ?",
        (rs, row) -> timelineRow(rs), leadId, organizationId));
    result.addAll(jdbc.query("SELECT f.id, 'follow_up' AS type, '跟进记录' AS title, f.summary AS description, u.name AS actor_name, f.occurred_at FROM follow_ups f JOIN users u ON u.id = f.created_by_id WHERE f.lead_id = ? AND f.organization_id = ? AND f.deleted_at IS NULL",
        (rs, row) -> timelineRow(rs), leadId, organizationId));
    result.sort((a, b) -> String.valueOf(b.get("occurredAt")).compareTo(String.valueOf(a.get("occurredAt"))));
    return result;
  }

  private Map<String, Object> timelineRow(ResultSet rs) throws SQLException {
    Map<String, Object> item = new LinkedHashMap<>(); item.put("id", rs.getObject("id")); item.put("type", rs.getString("type")); item.put("title", rs.getString("title")); item.put("description", rs.getString("description")); item.put("actorName", rs.getString("actor_name")); item.put("occurredAt", rs.getObject("occurred_at")); return item;
  }

  private Map<String, Object> summary(ResultSet rs, boolean detail) throws SQLException {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", rs.getObject("id")); result.put("name", rs.getString("name")); result.put("company", rs.getString("company")); result.put("phone", rs.getString("phone")); result.put("email", rs.getString("email")); result.put("source", rs.getString("source")); result.put("industry", rs.getString("industry")); result.put("region", rs.getString("region")); result.put("ownerId", rs.getObject("owner_id"));
    result.put("owner", rs.getObject("owner_id") == null ? null : Map.of("id", rs.getObject("owner_id"), "name", rs.getString("owner_name"), "email", rs.getString("owner_email")));
    result.put("stage", Map.of("id", rs.getObject("stage_id"), "code", rs.getString("stage_code"), "name", rs.getString("stage_name"), "color", rs.getString("stage_color"), "sortOrder", rs.getInt("sort_order"), "isDefault", rs.getBoolean("is_default"), "isWon", rs.getBoolean("is_won"), "isLost", rs.getBoolean("is_lost"), "isActive", rs.getBoolean("is_active")));
    result.put("nextFollowUpAt", rs.getObject("next_follow_up_at")); result.put("archivedAt", rs.getObject("archived_at")); result.put("createdAt", rs.getObject("created_at")); result.put("updatedAt", rs.getObject("updated_at"));
    if (detail) { result.put("notes", rs.getString("notes")); result.put("createdBy", Map.of("id", rs.getObject("created_by_id"), "name", rs.getString("creator_name"), "email", rs.getString("creator_email"))); }
    return result;
  }

  private void ensureOrgReference(UUID id, UUID organizationId, String message) { ensureOrgReference(id, organizationId, message, "users"); }
  private void ensureOrgReference(UUID id, UUID organizationId, String message, String table) {
    if (id == null) throw new ApiException("RESOURCE_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ? AND organization_id = ?", Integer.class, id, organizationId);
    if (count == null || count != 1) throw new ApiException("RESOURCE_NOT_FOUND", message, HttpStatus.NOT_FOUND);
  }

  private Map<String, Object> notFound() { throw new ApiException("RESOURCE_NOT_FOUND", "线索不存在", HttpStatus.NOT_FOUND); }
  private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private String normalizeEmail(String value) { String normalized = blank(value); return normalized == null ? null : normalized.toLowerCase(); }
  private String normalizePhone(String value) { String normalized = blank(value); return normalized == null ? null : normalized.replaceAll("[\\s-]", ""); }

  public record Query(Integer page, Integer pageSize, String keyword, UUID stageId, UUID ownerId, String source, String from, String to, Boolean archived, String sortBy, String sortOrder) {}
  public record CreateRequest(String name, String company, String phone, String email, @NotBlank @Size(max = 60) String source, String industry, String region, String notes, UUID stageId, UUID ownerId, List<UUID> tagIds, Instant nextFollowUpAt) {}
  public record UpdateRequest(String name, String company, String phone, String email, String source, String industry, String region, String notes, UUID ownerId, List<UUID> tagIds, Instant nextFollowUpAt) {}
  public record ChangeStageRequest(@NotNull UUID stageId, @Size(max = 500) String note, @Size(max = 500) String outcomeNote, @Size(max = 200) String lostReason) {}
  public record BatchAssignRequest(@NotNull @Size(max = 100) List<UUID> leadIds, @NotNull UUID ownerId) {}
}
