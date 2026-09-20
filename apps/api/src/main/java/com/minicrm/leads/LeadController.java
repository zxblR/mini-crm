package com.minicrm.leads;

import com.minicrm.common.ApiEnvelope;
import com.minicrm.common.CurrentUser;
import com.minicrm.common.RoleCode;
import com.minicrm.common.Roles;
import com.minicrm.common.SecurityUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/leads", "/api/v1/leads"})
public class LeadController {
  private final LeadService service;

  public LeadController(LeadService service) { this.service = service; }

  @GetMapping
  public ApiEnvelope<List<Map<String, Object>>> list(Authentication authentication,
      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String keyword, @RequestParam(required = false) UUID stageId,
      @RequestParam(required = false) UUID ownerId, @RequestParam(required = false) String source,
      @RequestParam(required = false) String from, @RequestParam(required = false) String to,
      @RequestParam(required = false) Boolean archived, @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) String sortOrder) {
    var result = service.list(CurrentUser.require(authentication), new LeadService.Query(page, pageSize, keyword, stageId, ownerId, source, from, to, archived, sortBy, sortOrder));
    return ApiEnvelope.ok(result.items(), result.meta());
  }

  @GetMapping("/{id}")
  public ApiEnvelope<Map<String, Object>> get(Authentication authentication, @PathVariable UUID id) {
    return ApiEnvelope.ok(service.get(CurrentUser.require(authentication), id));
  }

  @PostMapping
  @Roles({RoleCode.OWNER, RoleCode.ADMIN, RoleCode.SALES})
  public ResponseEntity<ApiEnvelope<Map<String, Object>>> create(Authentication authentication,
      @Valid @RequestBody LeadService.CreateRequest request, HttpServletRequest httpRequest) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.ok(service.create(CurrentUser.require(authentication), request, httpRequest)));
  }

  @PatchMapping("/{id}")
  public ApiEnvelope<Map<String, Object>> update(Authentication authentication, @PathVariable UUID id,
      @Valid @RequestBody LeadService.UpdateRequest request, HttpServletRequest httpRequest) {
    return ApiEnvelope.ok(service.update(CurrentUser.require(authentication), id, request, httpRequest));
  }

  @PostMapping("/{id}/archive")
  public ApiEnvelope<Map<String, Object>> archive(Authentication authentication, @PathVariable UUID id, HttpServletRequest request) {
    return ApiEnvelope.ok(service.archive(CurrentUser.require(authentication), id, false, request));
  }

  @PostMapping("/{id}/restore")
  public ApiEnvelope<Map<String, Object>> restore(Authentication authentication, @PathVariable UUID id, HttpServletRequest request) {
    return ApiEnvelope.ok(service.archive(CurrentUser.require(authentication), id, true, request));
  }

  @PostMapping("/{id}/assign")
  @Roles({RoleCode.OWNER, RoleCode.ADMIN})
  public ApiEnvelope<Map<String, Object>> assign(Authentication authentication, @PathVariable UUID id,
      @Valid @RequestBody AssignRequest request, HttpServletRequest httpRequest) {
    return ApiEnvelope.ok(service.assign(CurrentUser.require(authentication), id, request.ownerId(), httpRequest));
  }

  @PostMapping("/batch-assign")
  @Roles({RoleCode.OWNER, RoleCode.ADMIN})
  public ApiEnvelope<Map<String, Object>> batchAssign(Authentication authentication,
      @Valid @RequestBody LeadService.BatchAssignRequest request, HttpServletRequest httpRequest) {
    return ApiEnvelope.ok(service.batchAssign(CurrentUser.require(authentication), request, httpRequest));
  }

  @PostMapping("/{id}/stage")
  public ApiEnvelope<Map<String, Object>> changeStage(Authentication authentication, @PathVariable UUID id,
      @Valid @RequestBody LeadService.ChangeStageRequest request, HttpServletRequest httpRequest) {
    return ApiEnvelope.ok(service.changeStage(CurrentUser.require(authentication), id, request, httpRequest));
  }

  public record AssignRequest(@NotNull UUID ownerId) {}
}
