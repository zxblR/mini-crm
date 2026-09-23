package com.minicrm.leads;

import com.minicrm.common.JdbcTimeUtils;
import com.minicrm.common.SecurityUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportServiceTest {
  @Test
  void escapesSpreadsheetFormulaPrefixesAfterLeadingWhitespace() {
    assertThat(ExportService.escapeCell("  =SUM(A1:A2)")).isEqualTo("'  =SUM(A1:A2)");
    assertThat(ExportService.escapeCell("+447700900123")).isEqualTo("'+447700900123");
    assertThat(ExportService.escapeCell("ordinary value")).isEqualTo("ordinary value");
    assertThat(ExportService.escapeCell(null)).isEmpty();
  }

  @Test
  void dateFiltersBindAsOffsetDateTime() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    Instant from = Instant.parse("2026-09-01T00:00:00Z");
    Instant to = Instant.parse("2026-09-30T23:59:59Z");
    when(jdbc.queryForObject(anyString(), eq(Long.class), org.mockito.ArgumentMatchers.any(Object[].class)))
        .thenReturn(0L);
    SecurityUser admin = new SecurityUser(UUID.randomUUID(), UUID.randomUUID(), "Admin", "admin@example.com",
        null, List.of("ADMIN"), List.of());

    new ExportService(jdbc).export(admin, null, null, null, null, from.toString(), to.toString(), null, null);

    org.mockito.ArgumentCaptor<Object[]> args = org.mockito.ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).queryForObject(anyString(), eq(Long.class), args.capture());
    assertThat(args.getValue()).contains(OffsetDateTime.from(JdbcTimeUtils.toDbTime(from)));
    assertThat(args.getValue()).contains(OffsetDateTime.from(JdbcTimeUtils.toDbTime(to)));
  }
}
