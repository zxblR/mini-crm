package com.minicrm.tags;

import com.minicrm.common.ApiEnvelope;
import com.minicrm.common.CurrentUser;
import com.minicrm.common.RoleCode;
import com.minicrm.common.Roles;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/tags", "/api/v1/tags"})
public class TagController {
  private final TagService service;
  public TagController(TagService service) { this.service = service; }

  @GetMapping
  public ApiEnvelope<List<Map<String, Object>>> list(Authentication authentication, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String keyword) {
    var result = service.list(CurrentUser.require(authentication), page, pageSize, keyword);
    return ApiEnvelope.ok(result.items(), result.meta());
  }

  @PostMapping
  @Roles({RoleCode.OWNER, RoleCode.ADMIN})
  public ApiEnvelope<Map<String, Object>> create(Authentication authentication, @Valid @RequestBody TagService.Request request) { return ApiEnvelope.ok(service.create(CurrentUser.require(authentication), request)); }

  @PatchMapping("/{id}")
  @Roles({RoleCode.OWNER, RoleCode.ADMIN})
  public ApiEnvelope<Map<String, Object>> update(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody TagService.Request request) { return ApiEnvelope.ok(service.update(CurrentUser.require(authentication), id, request)); }
}
