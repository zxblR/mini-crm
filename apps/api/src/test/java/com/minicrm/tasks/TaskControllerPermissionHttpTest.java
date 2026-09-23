package com.minicrm.tasks;

import com.minicrm.common.ApiException;
import com.minicrm.common.ApiExceptionHandler;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerPermissionHttpTest {
  private final TaskService service = mock(TaskService.class);
  private final MockMvc mockMvc = MockMvcBuilders
      .standaloneSetup(new TaskController(service))
      .addInterceptors(new AuthenticationGuard(), new RolesGuard())
      .addFilters(new RequestIdFilter())
      .setControllerAdvice(new ApiExceptionHandler())
      .build();

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void supportCanReadTasks() throws Exception {
    var authentication = authenticate("SUPPORT");
    when(service.list(any(), any())).thenReturn(new PageSupport.Result<>(List.of(), PageSupport.meta(PageSupport.request(1, 20), 0)));

    mockMvc.perform(get("/api/v1/tasks").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
  }

  @Test
  void forbiddenSalesMutationIsMappedToForbidden() throws Exception {
    var authentication = authenticate("SALES");
    when(service.resolve(any(), any(), anyBoolean(), any(), any()))
        .thenThrow(new ApiException("FORBIDDEN", "当前用户没有执行此操作的权限", HttpStatus.FORBIDDEN));

    mockMvc.perform(post("/api/v1/tasks/{id}/complete", UUID.randomUUID()).principal(authentication))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void repeatedCompletionAndNonPendingStateRemainConflicts() throws Exception {
    var authentication = authenticate("ADMIN");
    when(service.resolve(any(), any(), anyBoolean(), any(), any()))
        .thenThrow(new ApiException("TASK_ALREADY_COMPLETED", "任务已经完成", HttpStatus.CONFLICT));

    mockMvc.perform(post("/api/v1/tasks/{id}/complete", UUID.randomUUID()).principal(authentication))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("TASK_ALREADY_COMPLETED"));
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
