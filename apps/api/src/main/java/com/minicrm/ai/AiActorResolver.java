package com.minicrm.ai;

import com.minicrm.common.CurrentUser;
import com.minicrm.common.SecurityUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class AiActorResolver {
  public AiDtos.AiActor resolve(Authentication authentication, String organizationId) {
    SecurityUser user = CurrentUser.require(authentication);
    UUID requestedOrganizationId;
    try {
      requestedOrganizationId = UUID.fromString(organizationId);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid organization id");
    }
    if (!user.organizationId().equals(requestedOrganizationId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "resource not found");
    }
    List<String> roles = user.roles();
    return new AiDtos.AiActor(
        user.id(),
        user.organizationId(),
        roles.contains("OWNER"),
        roles.contains("ADMIN"),
        roles.contains("SALES"),
        roles.contains("SUPPORT"));
  }
}
