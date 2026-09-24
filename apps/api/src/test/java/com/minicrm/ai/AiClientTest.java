package com.minicrm.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiClientTest {
  @Test
  void currentLeadContextContractIsConstructible() {
    UUID leadId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    AiDtos.LeadContext context = new AiDtos.LeadContext(leadId, "text", "NEW", ownerId, "");

    assertNotNull(context);
  }

  @Test
  void aiProviderExceptionTypeRemainsPartOfClientContract() {
    AiProperties properties = new AiProperties();
    ReflectionTestUtils.setField(properties, "serviceToken", "test");
    ReflectionTestUtils.setField(properties, "model", "mock-model");
    ReflectionTestUtils.setField(properties, "connectTimeoutMs", 2000);
    ReflectionTestUtils.setField(properties, "totalBudgetMs", 10000);
    ReflectionTestUtils.setField(properties, "readTimeoutMs", 8000);

    assertNotNull(properties);
    assertNotNull(AiClient.AiProviderException.class);
  }
}
