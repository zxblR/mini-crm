package com.minicrm.dashboard;

public record ChannelStatsDTO(String source, long leadCount, long wonCount, double conversionRate) {}
