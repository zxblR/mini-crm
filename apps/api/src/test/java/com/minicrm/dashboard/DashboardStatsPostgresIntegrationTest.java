package com.minicrm.dashboard;

import com.minicrm.common.SecurityUser;
import com.minicrm.common.JdbcTimeUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardStatsPostgresIntegrationTest {
  @Test
  void reportAggregateRunsAgainstDedicatedPostgresTestDatabase() throws Exception {
    String url = System.getenv("MINI_CRM_TEST_DATABASE_URL");
    String username = System.getenv("MINI_CRM_TEST_DATABASE_USERNAME");
    String password = System.getenv("MINI_CRM_TEST_DATABASE_PASSWORD");
    Assumptions.assumeTrue(url != null && !url.isBlank(),
        "Set MINI_CRM_TEST_DATABASE_URL to a dedicated mini_crm_test PostgreSQL database");
    assertThat(url).matches("^jdbc:postgresql://[^/]+/mini_crm_test(?:\\?.*)?$");

    var dataSource = new DriverManagerDataSource(url, username, password);
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getDatabaseProductName()).contains("PostgreSQL");
      assertThat(connection.getCatalog()).isEqualTo("mini_crm_test");
    }

    DashboardStatsService service = new DashboardStatsService(new JdbcTemplate(dataSource));
    UUID organizationId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID wonLeadId = UUID.randomUUID();
    UUID openLeadId = UUID.randomUUID();
    UUID lostLeadId = UUID.randomUUID();
    UUID wonFollowUpId = UUID.randomUUID();
    UUID lostFollowUpId = UUID.randomUUID();
    SecurityUser actor = new SecurityUser(userId, organizationId, "Admin", null, null,
        List.of("ADMIN"), List.of());
    var from = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    var to = OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    var filter = new DashboardStatsService.Filter(from, to, null);
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    transaction.executeWithoutResult(status -> {
      var createdAt = OffsetDateTime.of(2026, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC);
      var wonAt = OffsetDateTime.of(2026, 1, 20, 0, 0, 0, 0, ZoneOffset.UTC);
      var openAt = OffsetDateTime.of(2026, 1, 3, 0, 0, 0, 0, ZoneOffset.UTC);
      var lostAt = OffsetDateTime.of(2026, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC);
      jdbc.update("""
          INSERT INTO organizations (id, name, slug, timezone, is_active, created_at, updated_at)
          VALUES (?, ?, ?, 'UTC', true, ?, ?)
          """, organizationId, "Stats test", "stats-" + organizationId,
          JdbcTimeUtils.toDbTime(createdAt.toInstant()), JdbcTimeUtils.toDbTime(createdAt.toInstant()));
      jdbc.update("""
          INSERT INTO users (id, organization_id, name, password_hash, is_active, created_at, updated_at)
          VALUES (?, ?, ?, ?, true, ?, ?)
          """, userId, organizationId, "Stats tester", "not-a-real-password-hash",
          JdbcTimeUtils.toDbTime(createdAt.toInstant()), JdbcTimeUtils.toDbTime(createdAt.toInstant()));
      insertLead(jdbc, wonLeadId, organizationId, userId, "website", "won", createdAt, wonAt, null);
      insertLead(jdbc, openLeadId, organizationId, userId, "website", "new", openAt, null, null);
      insertLead(jdbc, lostLeadId, organizationId, userId, "referral", "lost", createdAt, lostAt, "price");
      insertFollowUp(jdbc, wonFollowUpId, organizationId, wonLeadId, userId, createdAt.plusHours(1));
      insertFollowUp(jdbc, lostFollowUpId, organizationId, lostLeadId, userId, createdAt.plusDays(2));

      StatsOverviewDTO overview = service.overview(actor, filter);
      assertThat(overview.totalLeads()).isEqualTo(3);
      assertThat(overview.newLeads()).isEqualTo(3);
      assertThat(overview.statusCounts()).containsExactly(
          new StatsOverviewDTO.StatusCountDTO("new", 1),
          new StatsOverviewDTO.StatusCountDTO("contacted", 0),
          new StatsOverviewDTO.StatusCountDTO("qualified", 0),
          new StatsOverviewDTO.StatusCountDTO("proposal", 0),
          new StatsOverviewDTO.StatusCountDTO("negotiation", 0),
          new StatsOverviewDTO.StatusCountDTO("won", 1),
          new StatsOverviewDTO.StatusCountDTO("lost", 1));
      assertThat(overview.conversionRate()).isEqualTo(1.0 / 3.0);
      assertThat(overview.avgDealCycleSeconds()).isEqualTo(18 * 86_400.0);
      assertThat(overview.respondedCount()).isEqualTo(2);
      assertThat(overview.unrespondedCount()).isEqualTo(1);
      assertThat(overview.avgResponseSeconds()).isEqualTo(88_200.0);

      var channels = service.channels(actor, filter, 1, 20).items();
      assertThat(channels).hasSize(2);
      assertThat(channels).filteredOn(item -> item.source().equals("website"))
          .singleElement().satisfies(item -> {
            assertThat(item.leadCount()).isEqualTo(2);
            assertThat(item.wonCount()).isEqualTo(1);
            assertThat(item.conversionRate()).isEqualTo(0.5);
          });

      var ranking = service.salesRanking(actor, filter, 1, 20).items();
      assertThat(ranking).singleElement().satisfies(item -> {
        assertThat(item.ownerId()).isEqualTo(userId);
        assertThat(item.followUpCount()).isEqualTo(2);
        assertThat(item.wonCount()).isEqualTo(1);
        assertThat(item.avgDealCycleSeconds()).isEqualTo(18 * 86_400.0);
      });

      assertThat(service.trend(actor, filter, Granularity.month)).singleElement().satisfies(point -> {
        assertThat(point.periodStart()).isEqualTo(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant());
        assertThat(point.leadCount()).isEqualTo(3);
        assertThat(point.wonCount()).isEqualTo(1);
      });
      assertThat(service.lossReasons(actor, filter, 1, 20).items())
          .containsExactly(new LossReasonStatsDTO("price", 1));

      ResponseTimeStatsDTO response = service.responseTimes(actor, filter);
      assertThat(response.respondedCount()).isEqualTo(2);
      assertThat(response.unrespondedCount()).isEqualTo(1);
      assertThat(response.avgResponseSeconds()).isEqualTo(88_200.0);
      assertThat(response.buckets()).extracting(ResponseTimeStatsDTO.Bucket::count)
          .containsExactly(0L, 1L, 1L, 0L, 1L);
      status.setRollbackOnly();
    });
  }

  private void insertLead(JdbcTemplate jdbc, UUID leadId, UUID organizationId, UUID ownerId,
      String source, String state, OffsetDateTime createdAt, OffsetDateTime closedAt, String lostReason) {
    String outcomeNote = closedAt == null ? null : "Synthetic result";
    jdbc.update("""
        INSERT INTO leads
          (id, organization_id, name, source, status, owner_id, created_by_id, created_at, updated_at,
           closed_at, outcome_note, lost_reason)
        VALUES (?, ?, 'Synthetic lead', ?, ?::lead_status, ?, ?, ?, ?, ?, ?, ?)
        """, leadId, organizationId, source, state, ownerId, ownerId,
        JdbcTimeUtils.toDbTime(createdAt.toInstant()), JdbcTimeUtils.toDbTime(createdAt.toInstant()),
        closedAt == null ? null : JdbcTimeUtils.toDbTime(closedAt.toInstant()), outcomeNote, lostReason);
  }

  private void insertFollowUp(JdbcTemplate jdbc, UUID followUpId, UUID organizationId, UUID leadId,
      UUID createdById, OffsetDateTime occurredAt) {
    jdbc.update("""
        INSERT INTO follow_ups
          (id, organization_id, lead_id, created_by_id, type, occurred_at, summary, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'call'::follow_up_type, ?, 'Synthetic follow-up', ?, ?)
        """, followUpId, organizationId, leadId, createdById,
        JdbcTimeUtils.toDbTime(occurredAt.toInstant()), JdbcTimeUtils.toDbTime(occurredAt.toInstant()),
        JdbcTimeUtils.toDbTime(occurredAt.toInstant()));
  }
}
