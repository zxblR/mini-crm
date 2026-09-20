package com.minicrm.jobs;

import com.minicrm.common.ApiEnvelope;
import com.minicrm.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api", "/api/v1"})
public class ImportJobController {
  private final ImportJobService service;
  public ImportJobController(ImportJobService service) { this.service = service; }

  @PostMapping("/leads/import")
  public ResponseEntity<ApiEnvelope<Map<String, Object>>> create(Authentication authentication, @RequestPart("file") MultipartFile file, HttpServletRequest request) { return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiEnvelope.ok(service.create(CurrentUser.require(authentication), file, request))); }

  @GetMapping("/jobs/{id}")
  public ApiEnvelope<Map<String, Object>> get(Authentication authentication, @PathVariable UUID id) { return ApiEnvelope.ok(service.get(CurrentUser.require(authentication), id)); }
}
