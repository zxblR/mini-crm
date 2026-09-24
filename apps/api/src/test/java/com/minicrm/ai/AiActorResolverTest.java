package com.minicrm.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minicrm.common.ApiException;
import com.minicrm.common.SecurityUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AiActorResolverTest {
  private final AiActorResolver resolver = new AiActorResolver();

  @Test
  void ownerCanTriggerAi() {
    SecurityUser user = securityUser("OWNER");

    AiDtos.AiActor actor = resolver.resolve(authentication(user), user.organizationId().toString());

    assertTrue(actor.canTrigger());
  }

  @Test
  void salesCanTriggerAi() {
    SecurityUser user = securityUser("SALES");

    AiDtos.AiActor actor = resolver.resolve(authentication(user), user.organizationId().toString());

    assertTrue(actor.canTrigger());
  }

  @Test
  void supportCanReadButCannotTriggerAi() {
    SecurityUser user = securityUser("SUPPORT");

    AiDtos.AiActor actor = resolver.resolve(authentication(user), user.organizationId().toString());

    assertFalse(actor.canTrigger());
    assertTrue(actor.support());
  }

  @Test
  void organizationMismatchIsNotFound() {
    SecurityUser user = securityUser("OWNER");

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> resolver.resolve(authentication(user), UUID.randomUUID().toString()));

    assertTrue(exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND));
  }

  @Test
  void nullAuthenticationIsUnauthorized() {
    assertThrows(ApiException.class, () -> resolver.resolve(null, UUID.randomUUID().toString()));
  }

  private SecurityUser securityUser(String role) {
    return new SecurityUser(UUID.randomUUID(), UUID.randomUUID(), "Test User", null, null,
        List.of(role), List.of());
  }

  private MockAuthentication authentication(SecurityUser user) {
    return new MockAuthentication(user);
  }

  private static final class MockAuthentication extends UsernamePasswordAuthenticationToken {
    private MockAuthentication(SecurityUser user) {
      super(user, null, user.roles().stream()
          .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
          .toList());
    }
  }
}
