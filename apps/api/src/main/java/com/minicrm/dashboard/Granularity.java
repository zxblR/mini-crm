package com.minicrm.dashboard;

public enum Granularity {
  day("day", "1 day"),
  week("week", "1 week"),
  month("month", "1 month");

  private final String sqlValue;
  private final String interval;

  Granularity(String sqlValue, String interval) {
    this.sqlValue = sqlValue;
    this.interval = interval;
  }

  public String sqlValue() {
    return sqlValue;
  }

  public String interval() {
    return interval;
  }
}
