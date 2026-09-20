package com.minicrm.auth;

import com.minicrm.common.SecurityUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {
  private final SecretKey key;
  private final long expirationSeconds;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiration-seconds}") long expirationSeconds) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationSeconds = expirationSeconds;
  }

  public String issue(SecurityUser user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.id().toString())
        .claim("organizationId", user.organizationId().toString())
        .claim("roles", user.roles())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(expirationSeconds)))
        .signWith(key)
        .compact();
  }

  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  public long expirationSeconds() {
    return expirationSeconds;
  }

  public static UUID uuid(Claims claims, String name) {
    return UUID.fromString(claims.get(name, String.class));
  }

  public static List<String> roles(Claims claims) {
    return claims.get("roles", List.class);
  }
}
