package com.minicrm.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiSecurityHandlers {
  private final ObjectMapper objectMapper;

  public ApiSecurityHandlers(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public AuthenticationEntryPoint authenticationEntryPoint() {
    return (request, response, exception) -> write(response, 401, "UNAUTHORIZED", "未认证");
  }

  public AccessDeniedHandler accessDeniedHandler() {
    return (request, response, exception) -> write(response, 403, "FORBIDDEN", "当前用户没有执行此操作的权限");
  }

  private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    objectMapper.writeValue(response.getOutputStream(), ApiEnvelope.failure(
        code, message, response.getHeader("X-Request-Id") == null ? "" : response.getHeader("X-Request-Id")));
  }
}
