package com.minicrm.pipeline;

import com.minicrm.common.ApiEnvelope;
import com.minicrm.common.BusinessRules;
import com.minicrm.common.CurrentUser;
import com.minicrm.common.RoleCode;
import com.minicrm.common.Roles;
import com.minicrm.common.SecurityUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/pipeline-stages", "/api/v1/pipeline-stages"})
public class PipelineStageController {
  private final PipelineStageService service;

  public PipelineStageController(PipelineStageService service) { this.service = service; }

  @GetMapping
  public ApiEnvelope<List<Map<String, Object>>> list(Authentication authentication,
      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) Boolean isActive) {
    SecurityUser actor = CurrentUser.require(authentication);
    var result = service.list(actor, page, pageSize, isActive);
    return ApiEnvelope.ok(result.items(), result.meta());
  }

  @PostMapping
  @Roles({RoleCode.OWNER, RoleCode.ADMIN})
  public ApiEnvelope<Map<String, Object>> create(Authentication authentication, @Valid @RequestBody PipelineStageService.CreateRequest request) {
    return ApiEnvelope.ok(service.create(CurrentUser.require(authentication), request));
  }

  @PatchMapping("/{id}")
  @Roles({RoleCode.OWNER, RoleCode.ADMIN})
  public ApiEnvelope<Map<String, Object>> update(Authentication authentication, @PathVariable UUID id,
      @Valid @RequestBody PipelineStageService.UpdateRequest request) {
    return ApiEnvelope.ok(service.update(CurrentUser.require(authentication), id, request));
  }

  @PostMapping("/reorder")
  @Roles({RoleCode.OWNER, RoleCode.ADMIN})
  public ApiEnvelope<List<Map<String, Object>>> reorder(Authentication authentication, @Valid @RequestBody ReorderRequest request) {
    return ApiEnvelope.ok(service.reorder(CurrentUser.require(authentication), request.stageIds()));
  }

  public record ReorderRequest(@NotEmpty List<UUID> stageIds) {}
}
