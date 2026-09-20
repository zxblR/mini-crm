package com.minicrm.auth;

import com.minicrm.common.SecurityUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
  private final JwtService jwtService = new JwtService(
      "development-secret-that-is-at-least-32-bytes-long",
      900);

  @Test
  void issuesAndParsesAccessToken() {
    SecurityUser user = new SecurityUser(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Demo User",
        "demo@example.com",
        null,
        List.of("OWNER"),
        List.of());

    var claims = jwtService.parse(jwtService.issue(user));

    assertThat(claims.getSubject()).isEqualTo(user.id().toString());
    assertThat(claims.get("organizationId", String.class)).isEqualTo(user.organizationId().toString());
    assertThat(JwtService.roles(claims)).containsExactly("OWNER");
  }
}
