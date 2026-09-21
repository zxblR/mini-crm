package com.minicrm.leads;

import com.minicrm.common.PageSupport;
import com.minicrm.common.SecurityUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class LeadRepository {
  private final JdbcTemplate jdbc;

  public LeadRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PageSupport.Result<Map<String, Object>> findPage(
      SecurityUser actor, Query query) {
    PageSupport.PageRequest paging = PageSupport.request(query.page(), query.pageSize());
    StringBuilder where = new StringBuilder(" WHERE l.organization_id = ? ");
    List<Object> args = new ArrayList<>(List.of(actor.organizationId()));
    if (query.salesOnly()) {
      where.append(" AND l.owner_id = ? ");
      args.add(actor.id());
    }
    if (query.keyword() != null && !query.keyword().isBlank()) {
      where.append(" AND (LOWER(COALESCE(l.name, '')) LIKE ? OR LOWER(COALESCE(l.company, '')) LIKE ? "
          + "OR LOWER(COALESCE(l.email, '')) LIKE ? OR COALESCE(l.phone, '') LIKE ?) ");
      String keyword = "%" + query.keyword().trim().toLowerCase(java.util.Locale.ROOT) + "%";
      args.addAll(List.of(keyword, keyword, keyword, keyword));
    }
    if (query.status() != null) {
      where.append(" AND l.status = ?::lead_status ");
      args.add(query.status().value());
    }
    if (query.ownerId() != null) {
      where.append(" AND l.owner_id = ? ");
      args.add(query.ownerId());
    }
    if (query.source() != null && !query.source().isBlank()) {
      where.append(" AND l.source = ? ");
      args.add(query.source().trim());
    }
    if (query.from() != null && !query.from().isBlank()) {
      where.append(" AND l.created_at >= ?::timestamptz ");
      args.add(query.from());
    }
    if (query.to() != null && !query.to().isBlank()) {
      where.append(" AND l.created_at <= ?::timestamptz ");
      args.add(query.to());
    }
    where.append(Boolean.TRUE.equals(query.archived())
        ? " AND l.archived_at IS NOT NULL "
        : " AND l.archived_at IS NULL ");

    Long total = jdbc.queryForObject("SELECT COUNT(*) FROM leads l" + where, Long.class, args.toArray());
    String sort = switch (query.sortBy() == null ? "updatedAt" : query.sortBy()) {
      case "createdAt" -> "l.created_at";
      case "nextFollowUpAt" -> "l.next_follow_up_at";
      case "company" -> "l.company";
      default -> "l.updated_at";
    };
    String order = "desc".equalsIgnoreCase(query.sortOrder()) ? "DESC" : "ASC";
    List<Object> listArgs = new ArrayList<>(args);
    listArgs.add(paging.pageSize());
    listArgs.add(paging.offset());
    List<Map<String, Object>> rows = jdbc.query("""
        SELECT l.id, l.name, l.company, l.phone, l.email, l.source, l.industry, l.region,
               l.owner_id, l.status, l.next_follow_up_at, l.archived_at, l.closed_at,
               l.outcome_note, l.lost_reason, l.created_at, l.updated_at,
               o.name AS owner_name, o.email AS owner_email
        FROM leads l
        LEFT JOIN users o ON o.id = l.owner_id
        """ + where + " ORDER BY " + sort + " " + order
        + " NULLS LAST, l.id LIMIT ? OFFSET ?", (rs, row) -> summary(rs), listArgs.toArray());
    for (Map<String, Object> row : rows) {
      row.put("tags", tags(actor.organizationId(), (UUID) row.get("id")));
    }
    return new PageSupport.Result<>(rows, PageSupport.meta(paging, total == null ? 0 : total));
  }

  public Map<String, Object> findVisible(SecurityUser actor, UUID id) {
    Map<String, Object> row = jdbc.query("""
        SELECT l.id, l.organization_id, l.name, l.company, l.phone, l.email, l.source,
               l.industry, l.region, l.notes, l.owner_id, l.created_by_id, l.status,
               l.next_follow_up_at, l.archived_at, l.closed_at, l.outcome_note,
               l.lost_reason, l.created_at, l.updated_at,
               o.name AS owner_name, o.email AS owner_email,
               c.name AS creator_name, c.email AS creator_email
        FROM leads l
        LEFT JOIN users o ON o.id = l.owner_id
        JOIN users c ON c.id = l.created_by_id
        WHERE l.id = ? AND l.organization_id = ?
        """, rs -> rs.next() ? detail(rs) : null, id, actor.organizationId());
    if (row == null || (querySales(actor) && !actor.id().equals(row.get("ownerId")))) {
      return null;
    }
    row.put("tags", tags(actor.organizationId(), id));
    row.put("timeline", timeline(actor.organizationId(), id));
    return row;
  }

  public Map<String, Object> findForUpdate(UUID organizationId, UUID id) {
    return jdbc.query("""
        SELECT id, organization_id, owner_id, status, archived_at, name, company, phone,
               email, source, industry, region, notes, next_follow_up_at, closed_at,
               outcome_note, lost_reason, created_at, updated_at, created_by_id
        FROM leads
        WHERE id = ? AND organization_id = ?
        FOR UPDATE
        """, rs -> rs.next() ? locked(rs) : null, id, organizationId);
  }

  public List<UUID> duplicateIds(UUID organizationId, String email, String phone, UUID ignoredId) {
    String sql = """
        SELECT id FROM leads
        WHERE organization_id = ?
          AND ((?::varchar IS NOT NULL AND normalized_email = ?::varchar)
            OR (?::varchar IS NOT NULL AND normalized_phone = ?::varchar))
        """ + (ignoredId == null ? "" : " AND id <> ? ") + " ORDER BY id LIMIT 100";
    List<Object> args = new ArrayList<>(List.of(organizationId, email, email, phone, phone));
    if (ignoredId != null) args.add(ignoredId);
    return jdbc.query(sql, (rs, row) -> rs.getObject("id", UUID.class), args.toArray());
  }

  public boolean userInOrganization(UUID organizationId, UUID id) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM users WHERE id = ? AND organization_id = ? AND is_active = true",
        Integer.class, id, organizationId);
    return count != null && count == 1;
  }

  public boolean tagsInOrganization(UUID organizationId, List<UUID> tagIds) {
    if (tagIds == null || tagIds.isEmpty()) return true;
    String placeholders = String.join(",", java.util.Collections.nCopies(tagIds.size(), "?"));
    List<Object> args = new ArrayList<>();
    args.add(organizationId);
    args.addAll(tagIds);
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM tags WHERE organization_id = ? AND id IN (" + placeholders + ")",
        Integer.class, args.toArray());
    return count != null && count == tagIds.size();
  }

  public UUID insert(UUID organizationId, LeadService.CreateRequest request, String email, String phone,
                     LeadStatus status, UUID ownerId, UUID actorId) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO leads (id, organization_id, name, company, phone, normalized_phone,
          email, normalized_email, source, industry, region, notes, status, owner_id,
          created_by_id, next_follow_up_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::lead_status, ?, ?, ?, now())
        """, id, organizationId, LeadContactNormalizer.blank(request.name()),
        LeadContactNormalizer.blank(request.company()), LeadContactNormalizer.blank(request.phone()), phone,
        LeadContactNormalizer.blank(request.email()), email, request.source().trim(),
        LeadContactNormalizer.blank(request.industry()), LeadContactNormalizer.blank(request.region()),
        LeadContactNormalizer.blank(request.notes()), status.value(), ownerId, actorId,
        request.nextFollowUpAt());
    return id;
  }

  public void update(UUID organizationId, UUID id, LeadService.UpdateRequest request,
                     String email, String phone, UUID ownerId) {
    jdbc.update("""
        UPDATE leads SET name = COALESCE(?, name), company = COALESCE(?, company),
          phone = COALESCE(?, phone), normalized_phone = COALESCE(?, normalized_phone),
          email = COALESCE(?, email), normalized_email = COALESCE(?, normalized_email),
          source = COALESCE(?, source), industry = COALESCE(?, industry),
          region = COALESCE(?, region), notes = COALESCE(?, notes),
          owner_id = COALESCE(?, owner_id), next_follow_up_at = COALESCE(?, next_follow_up_at),
          updated_at = now()
        WHERE id = ? AND organization_id = ?
        """, LeadContactNormalizer.blank(request.name()), LeadContactNormalizer.blank(request.company()),
        LeadContactNormalizer.blank(request.phone()), phone, LeadContactNormalizer.blank(request.email()), email,
        LeadContactNormalizer.blank(request.source()), LeadContactNormalizer.blank(request.industry()),
        LeadContactNormalizer.blank(request.region()), LeadContactNormalizer.blank(request.notes()), ownerId,
        request.nextFollowUpAt(), id, organizationId);
  }

  public void archive(UUID organizationId, UUID id, boolean restore) {
    jdbc.update("UPDATE leads SET archived_at = ?, updated_at = now() WHERE id = ? AND organization_id = ?",
        restore ? null : java.sql.Timestamp.from(Instant.now()), id, organizationId);
  }

  public void assign(UUID organizationId, UUID id, UUID ownerId) {
    jdbc.update("UPDATE leads SET owner_id = ?, updated_at = now() WHERE id = ? AND organization_id = ?",
        ownerId, id, organizationId);
  }

  public void replaceTags(UUID leadId, List<UUID> tagIds) {
    if (tagIds == null) return;
    jdbc.update("DELETE FROM lead_tags WHERE lead_id = ?", leadId);
    for (UUID tagId : tagIds) {
      jdbc.update("INSERT INTO lead_tags (lead_id, tag_id) VALUES (?, ?)", leadId, tagId);
    }
  }

  public void changeStatus(UUID organizationId, UUID id, LeadStateMachine.Transition transition,
                           String outcomeNote, String lostReason) {
    boolean terminal = transition.to().terminal();
    jdbc.update("""
        UPDATE leads SET status = ?::lead_status, closed_at = ?, outcome_note = ?, lost_reason = ?,
          updated_at = now()
        WHERE id = ? AND organization_id = ?
        """, transition.to().value(), terminal ? java.sql.Timestamp.from(Instant.now()) : null,
        terminal ? outcomeNote : null, transition.to() == LeadStatus.LOST ? lostReason : null,
        id, organizationId);
  }

  public UUID insertHistory(UUID leadId, LeadStateMachine.Transition transition, UUID actorId,
                            String note) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO stage_history
          (id, lead_id, from_status, to_status, actor_id, note)
        VALUES (?, ?, ?::lead_status, ?::lead_status, ?, ?)
        """, id, leadId, transition.from().value(), transition.to().value(), actorId,
        LeadContactNormalizer.blank(note));
    return id;
  }

  private boolean querySales(SecurityUser actor) {
    return com.minicrm.common.BusinessRules.isSales(actor);
  }

  private List<Map<String, Object>> tags(UUID organizationId, UUID leadId) {
    return jdbc.query("""
        SELECT t.id, t.name, t.color FROM tags t
        JOIN lead_tags lt ON lt.tag_id = t.id
        WHERE lt.lead_id = ? AND t.organization_id = ? ORDER BY t.name
        """, (rs, row) -> {
      Map<String, Object> value = new LinkedHashMap<>();
      value.put("id", rs.getObject("id")); value.put("name", rs.getString("name"));
      value.put("color", rs.getString("color")); return value;
    }, leadId, organizationId);
  }

  private List<Map<String, Object>> timeline(UUID organizationId, UUID leadId) {
    List<Map<String, Object>> values = jdbc.query("""
        SELECT h.id, 'status_changed' AS type, '状态变更' AS title, h.note AS description,
               u.name AS actor_name, h.created_at AS occurred_at
        FROM stage_history h JOIN leads l ON l.id = h.lead_id JOIN users u ON u.id = h.actor_id
        WHERE h.lead_id = ? AND l.organization_id = ?
        ORDER BY h.created_at DESC
        """, (rs, row) -> timelineRow(rs), leadId, organizationId);
    values.addAll(jdbc.query("""
        SELECT f.id, 'follow_up' AS type, '跟进记录' AS title, f.summary AS description,
               u.name AS actor_name, f.occurred_at
        FROM follow_ups f JOIN users u ON u.id = f.created_by_id
        WHERE f.lead_id = ? AND f.organization_id = ? AND f.deleted_at IS NULL
        """, (rs, row) -> timelineRow(rs), leadId, organizationId));
    values.sort((a, b) -> String.valueOf(b.get("occurredAt")).compareTo(String.valueOf(a.get("occurredAt"))));
    return values;
  }

  private Map<String, Object> summary(ResultSet rs) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", rs.getObject("id")); value.put("name", rs.getString("name"));
    value.put("company", rs.getString("company")); value.put("phone", rs.getString("phone"));
    value.put("email", rs.getString("email")); value.put("source", rs.getString("source"));
    value.put("industry", rs.getString("industry")); value.put("region", rs.getString("region"));
    value.put("ownerId", rs.getObject("owner_id")); value.put("owner", owner(rs));
    value.put("status", rs.getString("status")); value.put("nextFollowUpAt", rs.getObject("next_follow_up_at"));
    value.put("archivedAt", rs.getObject("archived_at")); value.put("closedAt", rs.getObject("closed_at"));
    value.put("outcomeNote", rs.getString("outcome_note")); value.put("lostReason", rs.getString("lost_reason"));
    value.put("createdAt", rs.getObject("created_at")); value.put("updatedAt", rs.getObject("updated_at"));
    return value;
  }

  private Map<String, Object> detail(ResultSet rs) throws SQLException {
    Map<String, Object> value = summary(rs);
    value.put("notes", rs.getString("notes"));
    Map<String, Object> creator = new LinkedHashMap<>();
    creator.put("id", rs.getObject("created_by_id")); creator.put("name", rs.getString("creator_name"));
    creator.put("email", rs.getString("creator_email")); value.put("createdBy", creator);
    return value;
  }

  private Map<String, Object> locked(ResultSet rs) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", rs.getObject("id")); value.put("ownerId", rs.getObject("owner_id"));
    value.put("status", LeadStatus.fromValue(rs.getString("status"))); value.put("archivedAt", rs.getObject("archived_at"));
    value.put("name", rs.getString("name")); value.put("company", rs.getString("company"));
    value.put("phone", rs.getString("phone")); value.put("email", rs.getString("email"));
    value.put("source", rs.getString("source")); value.put("industry", rs.getString("industry"));
    value.put("region", rs.getString("region")); value.put("notes", rs.getString("notes"));
    value.put("nextFollowUpAt", rs.getObject("next_follow_up_at")); value.put("closedAt", rs.getObject("closed_at"));
    value.put("outcomeNote", rs.getString("outcome_note")); value.put("lostReason", rs.getString("lost_reason"));
    return value;
  }

  private Map<String, Object> owner(ResultSet rs) throws SQLException {
    if (rs.getObject("owner_id") == null) return null;
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", rs.getObject("owner_id")); value.put("name", rs.getString("owner_name"));
    value.put("email", rs.getString("owner_email")); return value;
  }

  private Map<String, Object> timelineRow(ResultSet rs) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<>(); value.put("id", rs.getObject("id"));
    value.put("type", rs.getString("type")); value.put("title", rs.getString("title"));
    value.put("description", rs.getString("description")); value.put("actorName", rs.getString("actor_name"));
    value.put("occurredAt", rs.getObject("occurred_at")); return value;
  }

  public record Query(Integer page, Integer pageSize, String keyword, LeadStatus status, UUID ownerId,
                      String source, String from, String to, Boolean archived, String sortBy,
                      String sortOrder, boolean salesOnly) {}
}
