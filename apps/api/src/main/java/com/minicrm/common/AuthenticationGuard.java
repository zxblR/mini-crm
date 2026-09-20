package com.minicrm.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

@Component
public class AuthenticationGuard implements HandlerInterceptor {
  @Override
  public boolean preHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler) {
    if (!(handler instanceof HandlerMethod method) || isPublic(method)) {
      return true;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof SecurityUser)) {
      throw new ApiException("UNAUTHORIZED", "未认证", org.springframework.http.HttpStatus.UNAUTHORIZED);
    }
    return true;
  }

  private boolean isPublic(HandlerMethod handler) {
    Method method = handler.getMethod();
    return method.isAnnotationPresent(Public.class)
        || handler.getBeanType().isAnnotationPresent(Public.class);
  }
}
