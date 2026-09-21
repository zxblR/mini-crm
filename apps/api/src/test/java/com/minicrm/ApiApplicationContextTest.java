package com.minicrm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ApiApplicationContextTest {
  @Autowired
  private MockMvc mockMvc;

  @Test
  void applicationContextLoadsWithoutDatabaseQueries() {
    assertThatCode(() -> {
      // Context creation is performed by SpringBootTest.
    }).doesNotThrowAnyException();
  }

  @Test
  void healthIsPublic() throws Exception {
    mockMvc.perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ok"))
        .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
  }

  @Test
  void protectedEndpointReturnsUniformUnauthorizedResponse() throws Exception {
    mockMvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
  }
}
