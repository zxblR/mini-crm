package com.minicrm.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RolesGuardTest {
  private final RolesGuard guard = new RolesGuard();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void allowsRequiredRole() throws Exception {
    authenticateAs("ROLE_ADMIN");
    HandlerMethod handler = handler("adminOnly");

    assertThatCode(() -> guard.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handler))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsMissingRole() throws Exception {
    authenticateAs("ROLE_SALES");
    HandlerMethod handler = handler("adminOnly");

    assertThatThrownBy(() -> guard.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handler))
        .isInstanceOf(ApiException.class)
        .hasMessage("当前用户没有执行此操作的权限");
  }

  private void authenticateAs(String authority) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("user", null, List.of(new SimpleGrantedAuthority(authority))));
  }

  private HandlerMethod handler(String methodName) throws Exception {
    Method method = TestController.class.getDeclaredMethod(methodName);
    return new HandlerMethod(new TestController(), method);
  }

  static class TestController {
    @Roles(RoleCode.ADMIN)
    public void adminOnly() {}
  }
}
