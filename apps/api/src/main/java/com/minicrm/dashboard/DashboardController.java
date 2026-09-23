package com.minicrm.dashboard;

import com.minicrm.common.ApiEnvelope;
import com.minicrm.common.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
  private final DashboardService service;
  private final DashboardStatsService statsService;
  public DashboardController(DashboardService service, DashboardStatsService statsService) {
    this.service = service;
    this.statsService = statsService;
  }

  @GetMapping("/summary")
  public ApiEnvelope<Map<String, Object>> summary(Authentication authentication, @RequestParam(required = false) java.time.OffsetDateTime from, @RequestParam(required = false) java.time.OffsetDateTime to, @RequestParam(required = false) java.util.UUID ownerId, @RequestParam(required = false) String source) { return ApiEnvelope.ok(service.summary(CurrentUser.require(authentication), new DashboardService.Filter(from, to, ownerId, source))); }

  @GetMapping("/funnel")
  public ApiEnvelope<List<Map<String, Object>>> funnel(Authentication authentication, @RequestParam(required = false) java.time.OffsetDateTime from, @RequestParam(required = false) java.time.OffsetDateTime to, @RequestParam(required = false) java.util.UUID ownerId, @RequestParam(required = false) String source, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) { var result = service.funnel(CurrentUser.require(authentication), new DashboardService.Filter(from, to, ownerId, source), page, pageSize); return ApiEnvelope.ok(result.items(), result.meta()); }

  @GetMapping("/sources")
  public ApiEnvelope<List<Map<String, Object>>> sources(Authentication authentication, @RequestParam(required = false) java.time.OffsetDateTime from, @RequestParam(required = false) java.time.OffsetDateTime to, @RequestParam(required = false) java.util.UUID ownerId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) { var result = service.sources(CurrentUser.require(authentication), new DashboardService.Filter(from, to, ownerId, null), page, pageSize); return ApiEnvelope.ok(result.items(), result.meta()); }

  @GetMapping("/owners")
  public ApiEnvelope<List<Map<String, Object>>> owners(Authentication authentication, @RequestParam(required = false) java.time.OffsetDateTime from, @RequestParam(required = false) java.time.OffsetDateTime to, @RequestParam(required = false) String source, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) { var result = service.owners(CurrentUser.require(authentication), new DashboardService.Filter(from, to, null, source), page, pageSize); return ApiEnvelope.ok(result.items(), result.meta()); }

  @GetMapping("/recent-activities")
  public ApiEnvelope<List<Map<String, Object>>> activities(Authentication authentication, @RequestParam(required = false) java.time.OffsetDateTime from, @RequestParam(required = false) java.time.OffsetDateTime to, @RequestParam(required = false) java.util.UUID ownerId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) { var result = service.activities(CurrentUser.require(authentication), new DashboardService.Filter(from, to, ownerId, null), page, pageSize); return ApiEnvelope.ok(result.items(), result.meta()); }

  @GetMapping("/overview")
  public ApiEnvelope<StatsOverviewDTO> overview(Authentication authentication,
      @RequestParam(required = false) java.time.OffsetDateTime from,
      @RequestParam(required = false) java.time.OffsetDateTime to,
      @RequestParam(required = false) java.util.UUID ownerId) {
    return ApiEnvelope.ok(statsService.overview(CurrentUser.require(authentication),
        new DashboardStatsService.Filter(from, to, ownerId)));
  }

  @GetMapping("/channels")
  public ApiEnvelope<List<ChannelStatsDTO>> channels(Authentication authentication,
      @RequestParam(required = false) java.time.OffsetDateTime from,
      @RequestParam(required = false) java.time.OffsetDateTime to,
      @RequestParam(required = false) java.util.UUID ownerId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    var result = statsService.channels(CurrentUser.require(authentication),
        new DashboardStatsService.Filter(from, to, ownerId), page, pageSize);
    return ApiEnvelope.ok(result.items(), result.meta());
  }

  @GetMapping("/sales-ranking")
  public ApiEnvelope<List<SalesRankingDTO>> salesRanking(Authentication authentication,
      @RequestParam(required = false) java.time.OffsetDateTime from,
      @RequestParam(required = false) java.time.OffsetDateTime to,
      @RequestParam(required = false) java.util.UUID ownerId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    var result = statsService.salesRanking(CurrentUser.require(authentication),
        new DashboardStatsService.Filter(from, to, ownerId), page, pageSize);
    return ApiEnvelope.ok(result.items(), result.meta());
  }

  @GetMapping("/trend")
  public ApiEnvelope<List<TrendPointDTO>> trend(Authentication authentication,
      @RequestParam(required = false) java.time.OffsetDateTime from,
      @RequestParam(required = false) java.time.OffsetDateTime to,
      @RequestParam(required = false) Granularity granularity,
      @RequestParam(required = false) java.util.UUID ownerId) {
    return ApiEnvelope.ok(statsService.trend(CurrentUser.require(authentication),
        new DashboardStatsService.Filter(from, to, ownerId), granularity));
  }

  @GetMapping("/loss-reasons")
  public ApiEnvelope<List<LossReasonStatsDTO>> lossReasons(Authentication authentication,
      @RequestParam(required = false) java.time.OffsetDateTime from,
      @RequestParam(required = false) java.time.OffsetDateTime to,
      @RequestParam(required = false) java.util.UUID ownerId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    var result = statsService.lossReasons(CurrentUser.require(authentication),
        new DashboardStatsService.Filter(from, to, ownerId), page, pageSize);
    return ApiEnvelope.ok(result.items(), result.meta());
  }

  @GetMapping("/response-times")
  public ApiEnvelope<ResponseTimeStatsDTO> responseTimes(Authentication authentication,
      @RequestParam(required = false) java.time.OffsetDateTime from,
      @RequestParam(required = false) java.time.OffsetDateTime to,
      @RequestParam(required = false) java.util.UUID ownerId) {
    return ApiEnvelope.ok(statsService.responseTimes(CurrentUser.require(authentication),
        new DashboardStatsService.Filter(from, to, ownerId)));
  }
}
