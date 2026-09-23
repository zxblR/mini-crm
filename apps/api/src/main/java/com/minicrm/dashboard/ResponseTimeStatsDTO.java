package com.minicrm.dashboard;

import java.util.List;

public record ResponseTimeStatsDTO(
    long respondedCount,
    long unrespondedCount,
    Double avgResponseSeconds,
    List<Bucket> buckets) {
  public record Bucket(String name, long count) {}
}
