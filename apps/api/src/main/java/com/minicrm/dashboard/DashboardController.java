package com.minicrm.dashboard;

import com.minicrm.common.ApiEnvelope;
import com.minicrm.common.CurrentUser;
import com.minicrm.common.SecurityUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/dashboard", "/api/v1/dashboard"})
public class DashboardController {
  private final DashboardService service;
  public DashboardController(DashboardService service) { this.service = service; }

  @GetMapping("/summary")
  public ApiEnvelope<Map<String, Object>> summary(Authentication authentication, @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(required = false) java.util.UUID ownerId, @RequestParam(required = false) String source) { return ApiEnvelope.ok(service.summary(CurrentUser.require(authentication), new DashboardService.Filter(from, to, ownerId, source))); }

  @GetMapping("/funnel")
  public ApiEnvelope<List<Map<String, Object>>> funnel(Authentication authentication, @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(required = false) java.util.UUID ownerId, @RequestParam(required = false) String source, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) { var result = service.funnel(CurrentUser.require(authentication), new DashboardService.Filter(from, to, ownerId, source), page, pageSize); return ApiEnvelope.ok(result.items(), result.meta()); }

  @GetMapping("/sources")
  public ApiEnvelope<List<Map<String, Object>>> sources(Authentication authentication, @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(required = false) java.util.UUID ownerId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) { var result = service.sources(CurrentUser.require(authentication), new DashboardService.Filter(from, to, ownerId, null), page, pageSize); return ApiEnvelope.ok(result.items(), result.meta()); }

  @GetMapping("/owners")
  public ApiEnvelope<List<Map<String, Object>>> owners(Authentication authentication, @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(required = false) String source, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) { var result = service.owners(CurrentUser.require(authentication), new DashboardService.Filter(from, to, null, source), page, pageSize); return ApiEnvelope.ok(result.items(), result.meta()); }

  @GetMapping("/recent-activities")
  public ApiEnvelope<List<Map<String, Object>>> activities(Authentication authentication, @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(required = false) java.util.UUID ownerId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) { var result = service.activities(CurrentUser.require(authentication), new DashboardService.Filter(from, to, ownerId, null), page, pageSize); return ApiEnvelope.ok(result.items(), result.meta()); }
}
