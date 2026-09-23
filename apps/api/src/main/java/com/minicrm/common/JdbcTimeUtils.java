package com.minicrm.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class JdbcTimeUtils {
  private JdbcTimeUtils() {}

  public static OffsetDateTime toDbTime(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }

  public static Instant fromDbTime(ResultSet resultSet, String column) throws SQLException {
    OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
