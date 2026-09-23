package com.minicrm.dashboard;

import com.minicrm.common.ApiExceptionHandler;
import com.minicrm.common.ApiException;
import com.minicrm.common.AuthenticationGuard;
import com.minicrm.common.PageSupport;
import com.minicrm.common.RequestIdFilter;
import com.minicrm.common.RolesGuard;
import com.minicrm.common.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerHttpTest {
  private final DashboardService dashboard = mock(DashboardService.class);
  private final DashboardStatsService stats = mock(DashboardStatsService.class);
  private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboard, stats))
      .addInterceptors(new AuthenticationGuard(), new RolesGuard())
      .addFilters(new RequestIdFilter())
      .setControllerAdvice(new ApiExceptionHandler())
      .build();

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void overviewRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/dashboard/overview"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void salesCanReadOwnChannelStatsWithCompatibleMeta() throws Exception {
    var authentication = authenticate("SALES");
    when(stats.channels(any(), any(), nullable(Integer.class), nullable(Integer.class)))
        .thenReturn(new PageSupport.Result<>(List.of(new ChannelStatsDTO("website", 2, 1, 0.5)),
            PageSupport.meta(PageSupport.request(1, 20), 1)));
    mockMvc.perform(get("/api/v1/dashboard/channels").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].source").value("website"))
        .andExpect(jsonPath("$.meta.page").value(1))
        .andExpect(jsonPath("$.meta.total").value(1));
  }

  @Test
  void salesCannotRequestAnotherOwner() throws Exception {
    var authentication = authenticate("SALES");
    when(stats.overview(any(), any())).thenThrow(new ApiException("FORBIDDEN",
        "销售只能查看自己的数据", HttpStatus.FORBIDDEN));
    mockMvc.perform(get("/api/v1/dashboard/overview")
            .principal(authentication).param("ownerId", UUID.randomUUID().toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void unknownDashboardRouteIsNotFound() throws Exception {
    var authentication = authenticate("SUPPORT");
    mockMvc.perform(get("/api/v1/dashboard/not-a-report").principal(authentication))
        .andExpect(status().isNotFound());
  }

  private UsernamePasswordAuthenticationToken authenticate(String role) {
    var user = new SecurityUser(UUID.randomUUID(), UUID.randomUUID(), "Test", null, null, List.of(role), List.of());
    var authentication = new UsernamePasswordAuthenticationToken(user, null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }
}
