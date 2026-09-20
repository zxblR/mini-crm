package com.minicrm.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicrm.common.ApiExceptionHandler;
import com.minicrm.common.AuthenticationGuard;
import com.minicrm.common.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.core.context.SecurityContextHolder.clearContext;

class AuthControllerHttpTest {
  private final AuthService authService = mock(AuthService.class);
  private final MockMvc mockMvc = MockMvcBuilders
      .standaloneSetup(new AuthController(authService))
      .addInterceptors(new AuthenticationGuard())
      .addFilters(new RequestIdFilter())
      .setControllerAdvice(new ApiExceptionHandler())
      .build();

  @AfterEach
  void cleanup() {
    clearContext();
  }

  @Test
  void loginIsPublicAndReturnsAccessToken() throws Exception {
    when(authService.login(eq("admin@example.com"), eq("password123"), any()))
        .thenReturn(Map.of("accessToken", "access-token", "expiresIn", 900));

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(new ObjectMapper().writeValueAsString(
                Map.of("account", "admin@example.com", "password", "password123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("access-token"))
        .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
  }

  @Test
  void protectedMeRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }
}
