package com.minicrm.followups;

import com.minicrm.common.ApiExceptionHandler;
import com.minicrm.common.AuthenticationGuard;
import com.minicrm.common.PageSupport;
import com.minicrm.common.RequestIdFilter;
import com.minicrm.common.RolesGuard;
import com.minicrm.common.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FollowUpControllerHttpTest {
  private final FollowUpService service = mock(FollowUpService.class);
  private final MockMvc mockMvc = MockMvcBuilders
      .standaloneSetup(new FollowUpController(service))
      .addInterceptors(new AuthenticationGuard(), new RolesGuard())
      .addFilters(new RequestIdFilter())
      .setControllerAdvice(new ApiExceptionHandler())
      .build();

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void listUsesTheLeadScopedRouteAndPagingEnvelope() throws Exception {
    UUID leadId = UUID.randomUUID();
    var authentication = authenticate("SALES");
    when(service.list(any(), eq(leadId), any())).thenReturn(new PageSupport.Result<>(
        List.of(Map.of("id", UUID.randomUUID(), "summary", "call")), PageSupport.meta(PageSupport.request(1, 20), 1)));

    mockMvc.perform(get("/api/v1/leads/{leadId}/follow-ups", leadId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].summary").value("call"))
        .andExpect(jsonPath("$.meta.total").value(1));
  }

  private UsernamePasswordAuthenticationToken authenticate(String role) {
    var user = new SecurityUser(UUID.randomUUID(), UUID.randomUUID(), "Test", "test@example.com",
        null, List.of(role), List.of());
    var token = new UsernamePasswordAuthenticationToken(user, null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    SecurityContextHolder.getContext().setAuthentication(token);
    return token;
  }
}
