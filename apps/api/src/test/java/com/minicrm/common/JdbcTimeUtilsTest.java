package com.minicrm.common;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcTimeUtilsTest {
  @Test
  void convertsInstantToUtcOffsetDateTimeAndBack() {
    Instant instant = Instant.parse("2026-09-23T08:15:30.123456Z");

    OffsetDateTime dbValue = JdbcTimeUtils.toDbTime(instant);

    assertThat(dbValue).isEqualTo(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
    assertThat(dbValue.getOffset()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void keepsNullValuesNull() {
    assertThat(JdbcTimeUtils.toDbTime(null)).isNull();
  }

  @Test
  void readsPostgresOffsetDateTimeAsInstant() throws SQLException {
    ResultSet resultSet = mock(ResultSet.class);
    OffsetDateTime stored = OffsetDateTime.parse("2026-09-23T16:15:30.123456+08:00");
    when(resultSet.getObject("created_at", OffsetDateTime.class)).thenReturn(stored);

    assertThat(JdbcTimeUtils.fromDbTime(resultSet, "created_at")).isEqualTo(Instant.parse("2026-09-23T08:15:30.123456Z"));
  }

  @Test
  void readsNullDatabaseTimeAsNull() throws SQLException {
    ResultSet resultSet = mock(ResultSet.class);
    when(resultSet.getObject("created_at", OffsetDateTime.class)).thenReturn(null);

    assertThat(JdbcTimeUtils.fromDbTime(resultSet, "created_at")).isNull();
  }
}
