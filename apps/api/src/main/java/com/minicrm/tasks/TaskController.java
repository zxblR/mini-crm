package com.minicrm.tasks;

import com.minicrm.common.ApiEnvelope;
import com.minicrm.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
  private final TaskService service;
  public TaskController(TaskService service) { this.service = service; }

  @GetMapping
  public ApiEnvelope<List<Map<String, Object>>> list(Authentication authentication, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String status, @RequestParam(required = false) String dueFrom, @RequestParam(required = false) String dueTo, @RequestParam(required = false) UUID assigneeId, @RequestParam(required = false) UUID leadId) {
    var result = service.list(CurrentUser.require(authentication), new TaskService.Query(page, pageSize, status, dueFrom, dueTo, assigneeId, leadId));
    return ApiEnvelope.ok(result.items(), result.meta());
  }

  @GetMapping("/today")
  public ApiEnvelope<List<Map<String, Object>>> today(Authentication authentication,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    var result = service.today(CurrentUser.require(authentication), page, pageSize);
    return ApiEnvelope.ok(result.items(), result.meta());
  }

  @PatchMapping("/{id}")
  public ApiEnvelope<Map<String, Object>> update(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody TaskService.UpdateRequest request, HttpServletRequest httpRequest) { return ApiEnvelope.ok(service.update(CurrentUser.require(authentication), id, request, httpRequest)); }

  @PostMapping("/{id}/complete")
  public ApiEnvelope<Map<String, Object>> complete(Authentication authentication, @PathVariable UUID id, @RequestBody(required = false) ResolveRequest request, HttpServletRequest httpRequest) { return ApiEnvelope.ok(service.resolve(CurrentUser.require(authentication), id, true, request == null ? null : request.note(), httpRequest)); }

  @PostMapping("/{id}/cancel")
  public ApiEnvelope<Map<String, Object>> cancel(Authentication authentication, @PathVariable UUID id, @RequestBody(required = false) ResolveRequest request, HttpServletRequest httpRequest) { return ApiEnvelope.ok(service.resolve(CurrentUser.require(authentication), id, false, request == null ? null : request.note(), httpRequest)); }

  public record ResolveRequest(String note) {}
}
