package com.minicrm.common;

import org.springframework.security.core.Authentication;

public final class CurrentUser {
  private CurrentUser() {}

  public static SecurityUser require(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUser user)) {
      throw new ApiException("UNAUTHORIZED", "未认证", org.springframework.http.HttpStatus.UNAUTHORIZED);
    }
    return user;
  }
}
