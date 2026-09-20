package com.minicrm.health;

import com.minicrm.common.ApiEnvelope;
import com.minicrm.common.Public;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api", "/api/v1"})
@Public
public class HealthController {
  private final JdbcTemplate jdbc;

  public HealthController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/health")
  public ApiEnvelope<Map<String, String>> health() {
    return ApiEnvelope.ok(Map.of("status", "ok", "service", "api-java"));
  }

  @GetMapping("/ready")
  public ResponseEntity<ApiEnvelope<Map<String, Object>>> ready() {
    try {
      jdbc.queryForObject("SELECT 1", Integer.class);
      return ResponseEntity.ok(ApiEnvelope.ok(Map.of(
          "status", "ok",
          "service", "api-java",
          "dependencies", Map.of("database", "up"))));
    } catch (DataAccessException exception) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
          ApiEnvelope.failure(
              "DEPENDENCY_UNAVAILABLE",
              "数据库依赖不可用",
              Map.of(),
              null));
    }
  }
}
