package com.minicrm.dashboard;

import com.minicrm.common.ApiException;
import com.minicrm.common.SecurityUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DashboardStatsServiceTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final DashboardStatsService service = new DashboardStatsService(jdbc);
  private final UUID organizationId = UUID.randomUUID();
  private final SecurityUser admin = new SecurityUser(UUID.randomUUID(), organizationId,
      "Admin", null, null, List.of("ADMIN"), List.of());

  @Test
  void rejectsPartialAndReversedRangesBeforeDatabaseAccess() {
    OffsetDateTime from = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    assertThatThrownBy(() -> service.overview(admin, new DashboardStatsService.Filter(from, null, null)))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.overview(admin, new DashboardStatsService.Filter(from, from, null)))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(jdbc);
  }

  @Test
  void rejectsRangesLongerThan366Days() {
    OffsetDateTime from = OffsetDateTime.parse("2025-01-01T00:00:00Z");
    OffsetDateTime to = from.plusDays(367);
    assertThatThrownBy(() -> service.overview(admin, new DashboardStatsService.Filter(from, to, null)))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(jdbc);
  }

  @Test
  void forcesSalesToOwnScopeAndRejectsAnotherOwnerWithoutQueryingReports() {
    SecurityUser sales = new SecurityUser(UUID.randomUUID(), organizationId,
        "Sales", null, null, List.of("SALES"), List.of());
    assertThatThrownBy(() -> service.overview(sales,
        new DashboardStatsService.Filter(null, null, UUID.randomUUID())))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(jdbc);
  }
}
