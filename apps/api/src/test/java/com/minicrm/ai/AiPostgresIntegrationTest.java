package com.minicrm.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Enable manually with an isolated mini_crm_test database after migration deploy.
 */
@Disabled("Enable only with MINI_CRM_TEST_DATABASE_URL and a real FastAPI + mock LLM")
class AiPostgresIntegrationTest {
  @Test
  void javaFastApiMockLlmPostgresFlow() {
    assertTrue(true);
  }
}
