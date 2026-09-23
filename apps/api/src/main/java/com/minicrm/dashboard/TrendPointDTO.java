package com.minicrm.dashboard;

import java.time.Instant;

public record TrendPointDTO(Instant periodStart, long leadCount, long wonCount) {}
