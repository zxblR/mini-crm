package com.minicrm.leads;

import com.minicrm.common.ApiExceptionHandler;
import com.minicrm.common.AuthenticationGuard;
import com.minicrm.common.CurrentUser;
import com.minicrm.common.PageSupport;
import com.minicrm.common.RequestIdFilter;
import com.minicrm.common.RolesGuard;
import com.minicrm.common.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.core.context.SecurityContextHolder.clearContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeadControllerHttpTest {
  private final LeadService service = mock(LeadService.class);
  private final MockMvc mockMvc = MockMvcBuilders
      .standaloneSetup(new LeadController(service))
      .addInterceptors(new AuthenticationGuard(), new RolesGuard())
      .addFilters(new RequestIdFilter())
      .setControllerAdvice(new ApiExceptionHandler())
      .build();

  @AfterEach
  void cleanup() {
    clearContext();
  }

  @Test
  void supportCannotCreateLead() throws Exception {
    var authentication = authenticate("SUPPORT");

    mockMvc.perform(post("/api/v1/leads")
            .principal(authentication)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Read only\",\"source\":\"web\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

    verifyNoInteractions(service);
  }

  @Test
  void authenticatedSalesCanReadThroughVersionedRoute() throws Exception {
    var authentication = authenticate("SALES");
    when(service.list(any(), any())).thenReturn(new PageSupport.Result<>(
        List.of(Map.of("id", UUID.randomUUID(), "status", "new")),
        PageSupport.meta(nullSafePage(), 1)));

    mockMvc.perform(get("/api/v1/leads")
            .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("new"))
        .andExpect(jsonPath("$.meta.total").value(1));
  }

  private UsernamePasswordAuthenticationToken authenticate(String role) {
    var user = new SecurityUser(UUID.randomUUID(), UUID.randomUUID(), "Test", "test@example.com",
        null, List.of(role), List.of());
    var authentication = new UsernamePasswordAuthenticationToken(user, null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }

  private com.minicrm.common.PageSupport.PageRequest nullSafePage() {
    return PageSupport.request(1, 20);
  }
}
