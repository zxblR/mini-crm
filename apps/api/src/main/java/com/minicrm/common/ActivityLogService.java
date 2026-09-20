package com.minicrm.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class ActivityLogService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public ActivityLogService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public void record(
      SecurityUser actor,
      String action,
      String resourceType,
      UUID resourceId,
      Map<String, Object> metadata,
      HttpServletRequest request) {
    String metadataJson = null;
    if (metadata != null && !metadata.isEmpty()) {
      try {
        metadataJson = objectMapper.writeValueAsString(metadata);
      } catch (JsonProcessingException exception) {
        throw new ApiException("INTERNAL_ERROR", "操作日志序列化失败",
            org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }

    jdbc.update("""
        INSERT INTO activity_logs
          (id, organization_id, actor_id, action, resource_type, resource_id, request_id, ip_address, metadata)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
        """,
        UUID.randomUUID(),
        actor.organizationId(),
        actor.id(),
        action,
        resourceType,
        resourceId,
        requestId(request),
        request == null ? null : request.getRemoteAddr(),
        metadataJson);
  }

  private String requestId(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    Object value = request.getAttribute(RequestIdFilter.REQUEST_ID);
    return value == null ? null : value.toString();
  }
}
