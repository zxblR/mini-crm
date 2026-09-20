package com.minicrm.auth;

import com.minicrm.common.ApiException;
import com.minicrm.common.ActivityLogService;
import com.minicrm.common.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
  private final AuthUserService users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final ActivityLogService activityLogService;
  private final JdbcTemplate jdbc;
  private final SecureRandom secureRandom = new SecureRandom();

  public AuthService(
      AuthUserService users,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      ActivityLogService activityLogService,
      JdbcTemplate jdbc) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.activityLogService = activityLogService;
    this.jdbc = jdbc;
  }

  public Map<String, Object> login(String account, String password, HttpServletRequest request) {
    SecurityUser user = users.findByAccount(account);
    if (user == null || !passwordEncoder.matches(password, users.passwordHash(user.id()))) {
      throw new ApiException("AUTH_INVALID_CREDENTIALS", "账号或密码错误", HttpStatus.UNAUTHORIZED);
    }
    users.markLogin(user.id());
    activityLogService.record(user, "LOGIN", "USER", user.id(), null, request);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("accessToken", jwtService.issue(user));
    result.put("refreshToken", createRefreshToken(user, request));
    result.put("expiresIn", jwtService.expirationSeconds());
    result.put("user", user);
    return result;
  }

  @Transactional
  public Map<String, Object> refresh(String refreshToken, HttpServletRequest request) {
    RefreshRecord current = jdbc.query(
        "SELECT id, user_id, organization_id, expires_at, revoked_at FROM refresh_tokens WHERE token_hash = ? FOR UPDATE",
        rs -> rs.next()
            ? new RefreshRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("revoked_at") == null
                    ? null
                    : rs.getTimestamp("revoked_at").toInstant())
            : null,
        hash(refreshToken));
    if (current == null || current.revokedAt() != null) throw new ApiException("AUTH_TOKEN_REVOKED", "刷新令牌已撤销", HttpStatus.UNAUTHORIZED);
    if (current.expiresAt() == null || current.expiresAt().isBefore(Instant.now())) throw new ApiException("AUTH_TOKEN_EXPIRED", "刷新令牌已过期", HttpStatus.UNAUTHORIZED);
    SecurityUser user = users.findActiveUser(current.userId());
    if (user == null) throw new ApiException("AUTH_TOKEN_REVOKED", "用户不可用", HttpStatus.UNAUTHORIZED);
    String next = createRefreshToken(user, request);
    UUID nextId = jdbc.queryForObject("SELECT id FROM refresh_tokens WHERE token_hash = ?", UUID.class, hash(next));
    jdbc.update("UPDATE refresh_tokens SET revoked_at = now(), replaced_by_id = ?, last_used_at = now() WHERE id = ?", nextId, current.id());
    Map<String, Object> result = new LinkedHashMap<>(); result.put("accessToken", jwtService.issue(user)); result.put("refreshToken", next); result.put("expiresIn", jwtService.expirationSeconds()); result.put("user", user); return result;
  }

  public Map<String, Object> logout(SecurityUser user, String refreshToken) {
    jdbc.update("UPDATE refresh_tokens SET revoked_at = COALESCE(revoked_at, now()), last_used_at = now() WHERE token_hash = ? AND user_id = ? AND organization_id = ?", hash(refreshToken), user.id(), user.organizationId());
    return Map.of("loggedOut", true);
  }

  private String createRefreshToken(SecurityUser user, HttpServletRequest request) {
    byte[] bytes = new byte[48]; secureRandom.nextBytes(bytes); String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO refresh_tokens (id, organization_id, user_id, token_hash, family_id, expires_at, ip_address, user_agent) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, user.organizationId(), user.id(), hash(token), id, java.sql.Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS)), request == null ? null : request.getRemoteAddr(), request == null ? null : request.getHeader("User-Agent"));
    return token;
  }

  private String hash(String token) {
    if (token == null || token.isBlank()) throw new ApiException("AUTH_TOKEN_REVOKED", "刷新令牌无效", HttpStatus.UNAUTHORIZED);
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
  }

  private record RefreshRecord(UUID id, UUID userId, UUID organizationId, Instant expiresAt, Instant revokedAt) {}
}
