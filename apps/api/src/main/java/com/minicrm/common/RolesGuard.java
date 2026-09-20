package com.minicrm.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class RolesGuard implements HandlerInterceptor {
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod method)) {
      return true;
    }

    Roles roles = method.getMethodAnnotation(Roles.class);
    if (roles == null) {
      roles = method.getBeanType().getAnnotation(Roles.class);
    }
    if (roles == null || roles.value().length == 0) {
      return true;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ApiException("UNAUTHORIZED", "未认证", org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    boolean allowed = Arrays.stream(roles.value())
        .map(role -> "ROLE_" + role.name())
        .anyMatch(required -> authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals(required)));
    if (!allowed) {
      throw new ApiException("FORBIDDEN", "当前用户没有执行此操作的权限", org.springframework.http.HttpStatus.FORBIDDEN);
    }
    return true;
  }
}
