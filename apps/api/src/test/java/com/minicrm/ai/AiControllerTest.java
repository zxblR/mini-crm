package com.minicrm.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiControllerTest {
  @org.springframework.boot.test.mock.mockito.MockBean
  com.minicrm.auth.JwtAuthenticationFilter jwtAuthenticationFilter;

  @org.springframework.boot.test.mock.mockito.MockBean
  com.minicrm.auth.JwtService jwtService;

  @org.springframework.boot.test.mock.mockito.MockBean
  com.minicrm.auth.AuthUserService authUserService;
    @Autowired
    MockMvc mockMvc;

    @MockBean
    AiService aiService;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/leads/{leadId}/ai/intent-score", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forceRefresh\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "not-a-uuid", authorities = "SUPPORT")
    void malformedOrganizationHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/leads/{leadId}/ai/intent-score", UUID.randomUUID())
                        .header("X-Organization-Id", "bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forceRefresh\":false}"))
                .andExpect(status().isUnauthorized());
    }
}
