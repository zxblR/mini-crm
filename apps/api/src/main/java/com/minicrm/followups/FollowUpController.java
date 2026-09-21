package com.minicrm.followups;

import com.minicrm.common.ApiEnvelope;
import com.minicrm.common.CurrentUser;
import com.minicrm.common.RoleCode;
import com.minicrm.common.Roles;
import com.minicrm.common.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class FollowUpController {
  private final FollowUpService service;
  public FollowUpController(FollowUpService service) { this.service = service; }

  @GetMapping("/leads/{leadId}/follow-ups")
  public ApiEnvelope<List<Map<String, Object>>> list(Authentication authentication, @PathVariable UUID leadId,
      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) com.minicrm.common.FollowUpType type, @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
    var result = service.list(CurrentUser.require(authentication), leadId, new FollowUpService.Query(page, pageSize, type, from, to));
    return ApiEnvelope.ok(result.items(), result.meta());
  }

  @PostMapping("/leads/{leadId}/follow-ups")
  @Roles({RoleCode.OWNER, RoleCode.ADMIN, RoleCode.SALES})
  public ResponseEntity<ApiEnvelope<Map<String, Object>>> create(Authentication authentication, @PathVariable UUID leadId, @Valid @RequestBody FollowUpService.CreateRequest request, HttpServletRequest httpRequest) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.ok(service.create(CurrentUser.require(authentication), leadId, request, httpRequest)));
  }

  @GetMapping("/follow-ups/{id}")
  public ApiEnvelope<Map<String, Object>> get(Authentication authentication, @PathVariable UUID id) { return ApiEnvelope.ok(service.get(CurrentUser.require(authentication), id)); }

  @PatchMapping("/follow-ups/{id}")
  public ApiEnvelope<Map<String, Object>> update(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody FollowUpService.UpdateRequest request, HttpServletRequest httpRequest) { return ApiEnvelope.ok(service.update(CurrentUser.require(authentication), id, request, httpRequest)); }

  @DeleteMapping("/follow-ups/{id}")
  public ApiEnvelope<Map<String, Object>> delete(Authentication authentication, @PathVariable UUID id, HttpServletRequest httpRequest) { return ApiEnvelope.ok(service.delete(CurrentUser.require(authentication), id, httpRequest)); }
}
