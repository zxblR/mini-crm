package com.minicrm.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.util.UUID;

public final class BusinessRules {
  private BusinessRules() {}

  public static SecurityUser user(Authentication authentication) {
    return CurrentUser.require(authentication);
  }

  public static boolean has(SecurityUser user, RoleCode role) {
    return user.roles().contains(role.name());
  }

  public static boolean isAdmin(SecurityUser user) {
    return has(user, RoleCode.OWNER) || has(user, RoleCode.ADMIN);
  }

  public static boolean isSales(SecurityUser user) {
    return has(user, RoleCode.SALES) && !isAdmin(user);
  }

  public static boolean canReadAll(SecurityUser user) {
    return !isSales(user);
  }

  public static void requireAdmin(SecurityUser user) {
    if (!isAdmin(user)) {
      throw new ApiException("FORBIDDEN", "当前用户没有执行此操作的权限", HttpStatus.FORBIDDEN);
    }
  }

  public static void requireLeadOwnerOrAdmin(SecurityUser user, UUID ownerId) {
    if (!isAdmin(user) && (!isSales(user) || ownerId == null || !ownerId.equals(user.id()))) {
      throw new ApiException("FORBIDDEN", "当前用户没有执行此操作的权限", HttpStatus.FORBIDDEN);
    }
  }
}
