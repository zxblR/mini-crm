package com.minicrm.leads;

import com.minicrm.common.JdbcTimeUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LeadRepositoryJdbcTimeTest {
  @Test
  void insertBindsNextFollowUpAtAsOffsetDateTime() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    LeadRepository repository = new LeadRepository(jdbc);
    Instant dueAt = Instant.parse("2026-09-30T12:00:00Z");
    LeadService.CreateRequest request = new LeadService.CreateRequest(
        "Lead", null, null, null, "web", null, null, null, null, null, List.of(), dueAt);

    repository.insert(UUID.randomUUID(), request, null, null, LeadStatus.NEW,
        null, UUID.randomUUID());

    org.mockito.ArgumentCaptor<Object[]> args = org.mockito.ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(anyString(), args.capture());
    assertThat(args.getValue()).contains(JdbcTimeUtils.toDbTime(dueAt));
    assertThat(args.getValue()).anyMatch(OffsetDateTime.class::isInstance);
  }

  @Test
  void updateBindsNextFollowUpAtAsOffsetDateTime() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    LeadRepository repository = new LeadRepository(jdbc);
    Instant dueAt = Instant.parse("2026-09-30T12:00:00Z");
    LeadService.UpdateRequest request = new LeadService.UpdateRequest(
        null, null, null, null, null, null, null, null, null, List.of(), dueAt);

    repository.update(UUID.randomUUID(), UUID.randomUUID(), request, null, null, null);

    org.mockito.ArgumentCaptor<Object[]> args = org.mockito.ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(anyString(), args.capture());
    assertThat(args.getValue()).contains(JdbcTimeUtils.toDbTime(dueAt));
  }
}
