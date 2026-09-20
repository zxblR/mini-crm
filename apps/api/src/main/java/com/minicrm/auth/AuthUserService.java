package com.minicrm.auth;

import com.minicrm.common.SecurityUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthUserService {
  private final JdbcTemplate jdbc;

  public AuthUserService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public SecurityUser findByAccount(String account) {
    String normalizedEmail = account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
    String normalizedPhone = normalizePhone(account);
    return jdbc.query("""
        SELECT u.id, u.organization_id, u.name, u.email, u.phone,
               COALESCE(array_agg(ur.code::text) FILTER (WHERE ur.code IS NOT NULL), ARRAY[]::text[]) AS roles
        FROM users u
        LEFT JOIN user_roles ur ON ur.user_id = u.id
        WHERE u.is_active = true AND (u.normalized_email = ? OR u.normalized_phone = ?)
        GROUP BY u.id
        """, rs -> rs.next() ? new SecurityUser(
        rs.getObject("id", UUID.class),
        rs.getObject("organization_id", UUID.class),
        rs.getString("name"),
        rs.getString("email"),
        rs.getString("phone"),
        roles(rs),
        permissions(roles(rs))) : null, normalizedEmail, normalizedPhone);
  }

  public String passwordHash(UUID userId) {
    return jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
  }

  public SecurityUser findActiveUser(UUID userId) {
    return jdbc.query("""
        SELECT u.id, u.organization_id, u.name, u.email, u.phone,
               COALESCE(array_agg(ur.code::text) FILTER (WHERE ur.code IS NOT NULL), ARRAY[]::text[]) AS roles
        FROM users u LEFT JOIN user_roles ur ON ur.user_id = u.id
        WHERE u.id = ? AND u.is_active = true GROUP BY u.id
        """, rs -> rs.next() ? new SecurityUser(
        rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
        rs.getString("name"), rs.getString("email"), rs.getString("phone"),
        roles(rs), permissions(roles(rs))) : null, userId);
  }

  public void markLogin(UUID userId) {
    jdbc.update("UPDATE users SET last_login_at = now() WHERE id = ?", userId);
  }

  private List<String> roles(ResultSet rs) throws SQLException {
    Array array = rs.getArray("roles");
    if (array == null || array.getArray() == null) {
      return List.of();
    }
    return new ArrayList<>(Arrays.asList((String[]) array.getArray()));
  }

  private List<String> permissions(List<String> roles) {
    List<String> result = new ArrayList<>();
    if (roles.contains("OWNER") || roles.contains("ADMIN")) {
      result.addAll(List.of(
          "user:read", "user:write", "team:read", "team:write",
          "lead:read", "lead:write", "lead:assign",
          "followup:read", "followup:write", "task:read", "task:write",
          "dashboard:read", "audit:read"));
    } else if (roles.contains("SALES")) {
      result.addAll(List.of(
          "lead:read", "lead:write", "followup:read", "followup:write",
          "task:read", "task:write", "dashboard:read"));
    } else if (roles.contains("SUPPORT")) {
      result.addAll(List.of("lead:read", "followup:read", "task:read", "dashboard:read"));
    }
    return result;
  }

  private String normalizePhone(String value) {
    return value == null ? "" : value.trim().replaceAll("[\\s-]", "");
  }
}
