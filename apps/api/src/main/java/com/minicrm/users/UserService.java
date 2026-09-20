package com.minicrm.users;

import com.minicrm.common.ActivityLogService;
import com.minicrm.common.ApiException;
import com.minicrm.common.PageSupport;
import com.minicrm.common.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;
  private final ActivityLogService activityLogService;

  public UserService(
      JdbcTemplate jdbc,
      PasswordEncoder passwordEncoder,
      ActivityLogService activityLogService) {
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
    this.activityLogService = activityLogService;
  }

  public PageSupport.Result<Map<String, Object>> list(
      SecurityUser actor,
      Integer page,
      Integer pageSize,
      String keyword,
      Boolean isActive,
      String role) {
    PageSupport.PageRequest paging = PageSupport.request(page, pageSize);
    String normalizedRole = normalizeRole(role);
    StringBuilder where = new StringBuilder(" WHERE u.organization_id = ? ");
    List<Object> args = new ArrayList<>();
    args.add(actor.organizationId());
    if (keyword != null && !keyword.isBlank()) {
      where.append(
          " AND (LOWER(COALESCE(u.name, '')) LIKE ? "
              + "OR LOWER(COALESCE(u.email, '')) LIKE ? "
              + "OR COALESCE(u.phone, '') LIKE ?) ");
      String value = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      args.add(value);
      args.add(value);
      args.add(value);
    }
    if (isActive != null) {
      where.append(" AND u.is_active = ? ");
      args.add(isActive);
    }
    if (normalizedRole != null) {
      where.append(
          " AND EXISTS (SELECT 1 FROM user_roles ur_filter "
              + "WHERE ur_filter.user_id = u.id AND ur_filter.code = ?::role_code) ");
      args.add(normalizedRole);
    }

    Long total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM users u" + where,
        Long.class,
        args.toArray());
    List<Object> listArgs = new ArrayList<>(args);
    listArgs.add(paging.pageSize());
    listArgs.add(paging.offset());
    List<Map<String, Object>> items = jdbc.query(
        """
            SELECT u.id, u.organization_id, u.name, u.email, u.phone, u.is_active,
                   u.last_login_at, u.created_at,
                   COALESCE(array_agg(ur.code::text) FILTER (WHERE ur.code IS NOT NULL), ARRAY[]::text[]) AS role_codes
            FROM users u
            LEFT JOIN user_roles ur ON ur.user_id = u.id
            """
            + where
            + " GROUP BY u.id ORDER BY u.created_at LIMIT ? OFFSET ?",
        (rs, rowNum) -> summary(rs),
        listArgs.toArray());
    return new PageSupport.Result<>(
        items,
        PageSupport.meta(paging, total == null ? 0 : total));
  }

  @Transactional
  public Map<String, Object> updateMe(
      SecurityUser actor,
      ProfileRequest request,
      HttpServletRequest httpRequest) {
    Map<String, Object> current = findSummary(actor.id(), actor.organizationId());
    if (current == null) {
      throw notFound();
    }
    String email = request.email() == null
        ? (String) current.get("email")
        : blankToNull(request.email());
    String phone = request.phone() == null
        ? (String) current.get("phone")
        : blankToNull(request.phone());
    requireContact(email, phone);
    try {
      jdbc.update(
          """
              UPDATE users SET name = COALESCE(?, name), email = ?, normalized_email = ?,
              phone = ?, normalized_phone = ?, updated_at = now()
              WHERE id = ? AND organization_id = ?
              """,
          blankToNull(request.name()),
          email,
          normalize(email),
          phone,
          normalize(phone),
          actor.id(),
          actor.organizationId());
    } catch (DataIntegrityViolationException exception) {
      throw duplicate();
    }
    activityLogService.record(
        actor,
        "UPDATE_USER",
        "USER",
        actor.id(),
        null,
        httpRequest);
    return findSummary(actor.id(), actor.organizationId());
  }

  @Transactional
  public Map<String, Object> create(
      SecurityUser actor,
      CreateUserRequest request,
      HttpServletRequest httpRequest) {
    requireContact(request.email(), request.phone());
    List<String> roles = validateRoles(request.roleCodes());
    UUID id = UUID.randomUUID();
    try {
      jdbc.update(
          """
              INSERT INTO users
                (id, organization_id, name, email, normalized_email, phone, normalized_phone, password_hash)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?)
              """,
          id,
          actor.organizationId(),
          request.name().trim(),
          blankToNull(request.email()),
          normalize(request.email()),
          blankToNull(request.phone()),
          normalizePhone(request.phone()),
          passwordEncoder.encode(request.password()));
      insertRoles(id, roles);
    } catch (DataIntegrityViolationException exception) {
      throw duplicate();
    }

    activityLogService.record(
        actor,
        "CREATE_USER",
        "USER",
        id,
        null,
        httpRequest);
    return findSummary(id, actor.organizationId());
  }

  @Transactional
  public Map<String, Object> updateStatus(
      SecurityUser actor,
      UUID id,
      boolean active,
      HttpServletRequest httpRequest) {
    Map<String, Object> current = findSummary(id, actor.organizationId());
    if (current == null) {
      throw notFound();
    }
    if (!active
        && Boolean.TRUE.equals(current.get("isActive"))
        && hasAdminRole(roles(current))
        && isLastActiveAdmin(actor.organizationId(), id)) {
      throw lastAdmin();
    }
    jdbc.update(
        """
            UPDATE users SET is_active = ?, updated_at = now()
            WHERE id = ? AND organization_id = ?
            """,
        active,
        id,
        actor.organizationId());
    activityLogService.record(
        actor,
        "UPDATE_USER_STATUS",
        "USER",
        id,
        Map.of("isActive", active),
        httpRequest);
    return findSummary(id, actor.organizationId());
  }

  @Transactional
  public Map<String, Object> update(
      SecurityUser actor,
      UUID id,
      UpdateUserRequest request,
      HttpServletRequest httpRequest) {
    Map<String, Object> current = findSummary(id, actor.organizationId());
    if (current == null) {
      throw notFound();
    }
    List<String> effectiveRoles = request.roleCodes() == null
        ? roles(current)
        : validateRoles(request.roleCodes());
    String email = request.email() == null
        ? (String) current.get("email")
        : blankToNull(request.email());
    String phone = request.phone() == null
        ? (String) current.get("phone")
        : blankToNull(request.phone());
    requireContact(email, phone);

    boolean wasAdmin = hasAdminRole(roles(current));
    boolean willBeAdmin = hasAdminRole(effectiveRoles);
    boolean active = request.isActive() == null
        ? Boolean.TRUE.equals(current.get("isActive"))
        : request.isActive();
    if (wasAdmin && (!willBeAdmin || !active) && isLastActiveAdmin(actor.organizationId(), id)) {
      throw lastAdmin();
    }

    try {
      jdbc.update(
          """
              UPDATE users SET name = COALESCE(?, name), email = ?, normalized_email = ?,
                phone = ?, normalized_phone = ?, is_active = ?, updated_at = now()
              WHERE id = ? AND organization_id = ?
              """,
          blankToNull(request.name()),
          email,
          normalize(email),
          phone,
          normalizePhone(phone),
          active,
          id,
          actor.organizationId());
      if (request.roleCodes() != null) {
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", id);
        insertRoles(id, effectiveRoles);
      }
    } catch (DataIntegrityViolationException exception) {
      throw duplicate();
    }
    activityLogService.record(
        actor,
        "UPDATE_USER",
        "USER",
        id,
        null,
        httpRequest);
    return findSummary(id, actor.organizationId());
  }

  private void insertRoles(UUID userId, List<String> roles) {
    for (String role : roles) {
      jdbc.update(
          "INSERT INTO user_roles (user_id, code) VALUES (?, ?::role_code)",
          userId,
          role);
    }
  }

  private boolean isLastActiveAdmin(UUID organizationId, UUID userId) {
    Integer count = jdbc.queryForObject(
        """
            SELECT COUNT(*) FROM users u
            JOIN user_roles ur ON ur.user_id = u.id
            WHERE u.organization_id = ? AND u.is_active = true AND u.id <> ?
              AND ur.code IN ('OWNER'::role_code, 'ADMIN'::role_code)
            """,
        Integer.class,
        organizationId,
        userId);
    return count != null && count == 0;
  }

  private Map<String, Object> findSummary(UUID userId, UUID organizationId) {
    return jdbc.query(
        """
            SELECT u.id, u.organization_id, u.name, u.email, u.phone, u.is_active,
                   u.last_login_at, u.created_at,
                   COALESCE(array_agg(ur.code::text) FILTER (WHERE ur.code IS NOT NULL), ARRAY[]::text[]) AS role_codes
            FROM users u LEFT JOIN user_roles ur ON ur.user_id = u.id
            WHERE u.id = ? AND u.organization_id = ?
            GROUP BY u.id
            """,
        rs -> rs.next() ? summary(rs) : null,
        userId,
        organizationId);
  }

  private Map<String, Object> summary(ResultSet rs) throws SQLException {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", rs.getObject("id"));
    result.put("organizationId", rs.getObject("organization_id"));
    result.put("name", rs.getString("name"));
    result.put("email", rs.getString("email"));
    result.put("phone", rs.getString("phone"));
    result.put("roleCodes", roles(rs));
    result.put("isActive", rs.getBoolean("is_active"));
    result.put("lastLoginAt", rs.getObject("last_login_at"));
    result.put("createdAt", rs.getObject("created_at"));
    return result;
  }

  @SuppressWarnings("unchecked")
  private List<String> roles(Map<String, Object> summary) {
    return (List<String>) summary.get("roleCodes");
  }

  private List<String> roles(ResultSet rs) throws SQLException {
    Array array = rs.getArray("role_codes");
    if (array == null || array.getArray() == null) {
      return List.of();
    }
    return new ArrayList<>(Arrays.asList((String[]) array.getArray()));
  }

  private List<String> validateRoles(List<String> roles) {
    if (roles == null || roles.isEmpty()) {
      throw new ApiException("VALIDATION_FAILED", "至少需要一个角色", HttpStatus.BAD_REQUEST);
    }
    List<String> normalized = roles.stream()
        .map(role -> role == null ? "" : role.trim().toUpperCase(Locale.ROOT))
        .distinct()
        .toList();
    if (normalized.stream().anyMatch(role -> !role.matches("OWNER|ADMIN|SALES|SUPPORT"))) {
      throw new ApiException("VALIDATION_FAILED", "角色代码无效", HttpStatus.BAD_REQUEST);
    }
    return normalized;
  }

  private String normalizeRole(String role) {
    if (role == null || role.isBlank()) {
      return null;
    }
    String normalized = role.trim().toUpperCase(Locale.ROOT);
    if (!normalized.matches("OWNER|ADMIN|SALES|SUPPORT")) {
      throw new ApiException("VALIDATION_FAILED", "角色代码无效", HttpStatus.BAD_REQUEST);
    }
    return normalized;
  }

  private boolean hasAdminRole(List<String> roles) {
    return roles.contains("OWNER") || roles.contains("ADMIN");
  }

  private void requireContact(String email, String phone) {
    if (blankToNull(email) == null && blankToNull(phone) == null) {
      throw new ApiException(
          "VALIDATION_FAILED",
          "邮箱或手机号至少填写一个",
          HttpStatus.BAD_REQUEST);
    }
  }

  private ApiException notFound() {
    return new ApiException("RESOURCE_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
  }

  private ApiException duplicate() {
    return new ApiException("USER_DUPLICATE", "邮箱或手机号已存在", HttpStatus.CONFLICT);
  }

  private ApiException lastAdmin() {
    return new ApiException(
        "LAST_ADMIN_REQUIRED",
        "组织至少需要一个活动管理员",
        HttpStatus.CONFLICT);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String normalize(String value) {
    String normalized = blankToNull(value);
    return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
  }

  private String normalizePhone(String value) {
    String normalized = blankToNull(value);
    return normalized == null ? null : normalized.replaceAll("[\\s-]", "");
  }

  public record ProfileRequest(String name, String email, String phone) {}

  public record CreateUserRequest(
      String name,
      String email,
      String phone,
      String password,
      List<String> roleCodes) {}

  public record UpdateUserRequest(
      String name,
      String email,
      String phone,
      List<String> roleCodes,
      Boolean isActive) {}
}
