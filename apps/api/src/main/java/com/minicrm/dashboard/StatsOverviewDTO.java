package com.minicrm.dashboard;

import java.util.List;

public record StatsOverviewDTO(
    long totalLeads,
    long newLeads,
    List<StatusCountDTO> statusCounts,
    double conversionRate,
    Double avgDealCycleSeconds,
    long respondedCount,
    long unrespondedCount,
    Double avgResponseSeconds) {
  public record StatusCountDTO(String status, long count) {}
}
