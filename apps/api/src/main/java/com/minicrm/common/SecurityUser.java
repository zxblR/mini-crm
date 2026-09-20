package com.minicrm.common;

import java.util.List;
import java.util.UUID;

public record SecurityUser(
    UUID id,
    UUID organizationId,
    String name,
    String email,
    String phone,
    List<String> roles,
    List<String> permissions) {
  public SecurityUser {
    roles = List.copyOf(roles);
    permissions = List.copyOf(permissions);
  }
}
