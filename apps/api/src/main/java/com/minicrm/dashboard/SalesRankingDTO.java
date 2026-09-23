package com.minicrm.dashboard;

import java.util.UUID;

public record SalesRankingDTO(
    UUID ownerId,
    String ownerName,
    long followUpCount,
    long wonCount,
    Double avgDealCycleSeconds,
    double conversionRate) {}
