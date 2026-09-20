package com.minicrm.jobs;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ImportJobProcessor {
  private final JdbcTemplate jdbc;

  public ImportJobProcessor(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Async("importTaskExecutor")
  public void processAsync(UUID jobId, UUID organizationId, UUID actorId, Path file) {
    int processed = 0;
    int succeeded = 0;
    int failed = 0;
    try {
      jdbc.update(
          "UPDATE import_jobs SET status = 'running'::import_job_status, started_at = now() WHERE id = ? AND organization_id = ?",
          jobId,
          organizationId);
      UUID defaultStage = defaultStage(organizationId);
      if (defaultStage == null) {
        finish(jobId, organizationId, "没有可用的活动阶段", processed, succeeded, failed, false);
        return;
      }

      try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
        String headerLine = reader.readLine();
        if (headerLine == null) {
          finish(jobId, organizationId, "CSV 文件为空", processed, succeeded, failed, false);
          return;
        }
        List<String> headers = normalizeHeaders(parseCsvLine(headerLine));
        Map<String, Integer> columns = indexHeaders(headers);
        if (!columns.containsKey("source")
            || (!columns.containsKey("name") && !columns.containsKey("company"))) {
          finish(jobId, organizationId, "CSV 必须包含 source，以及 name 或 company 列", processed, succeeded, failed, false);
          return;
        }

        String line;
        while ((line = reader.readLine()) != null) {
          if (line.isBlank()) {
            continue;
          }
          processed++;
          try {
            List<String> values = parseCsvLine(line);
            String name = value(values, columns, "name");
            String company = value(values, columns, "company");
            String source = value(values, columns, "source");
            String phone = value(values, columns, "phone");
            String email = value(values, columns, "email");
            if (blank(name) == null && blank(company) == null || blank(source) == null) {
              failed++;
            } else if (duplicate(organizationId, email, phone)) {
              failed++;
            } else {
              UUID ownerId = parseOwner(organizationId, value(values, columns, "ownerId"));
              jdbc.update(
                  """
                      INSERT INTO leads
                        (id, organization_id, name, company, phone, normalized_phone,
                         email, normalized_email, source, industry, region, notes,
                         stage_id, owner_id, created_by_id, next_follow_up_at)
                      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                      """,
                  UUID.randomUUID(),
                  organizationId,
                  blank(name),
                  blank(company),
                  blank(phone),
                  normalizePhone(phone),
                  blank(email),
                  normalizeEmail(email),
                  source.trim(),
                  blank(value(values, columns, "industry")),
                  blank(value(values, columns, "region")),
                  blank(value(values, columns, "notes")),
                  defaultStage,
                  ownerId,
                  actorId,
                  parseInstant(value(values, columns, "nextFollowUpAt")));
              succeeded++;
            }
          } catch (RuntimeException exception) {
            failed++;
          }
          updateCounts(jobId, organizationId, processed, succeeded, failed);
        }
      }
      finish(jobId, organizationId, null, processed, succeeded, failed, true);
    } catch (IOException | DataAccessException exception) {
      finish(jobId, organizationId, "导入处理失败", processed, succeeded, failed, false);
    } finally {
      try {
        Files.deleteIfExists(file);
      } catch (IOException ignored) {
        // Best effort cleanup; the database job remains the source of truth.
      }
    }
  }

  private UUID defaultStage(UUID organizationId) {
    return jdbc.query(
        "SELECT id FROM pipeline_stages WHERE organization_id = ? AND is_active = true ORDER BY is_default DESC, sort_order LIMIT 1",
        rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
        organizationId);
  }

  private boolean duplicate(UUID organizationId, String email, String phone) {
    String normalizedEmail = normalizeEmail(email);
    String normalizedPhone = normalizePhone(phone);
    if (normalizedEmail == null && normalizedPhone == null) {
      return false;
    }
    Integer count = jdbc.queryForObject(
        """
            SELECT COUNT(*) FROM leads
            WHERE organization_id = ?
              AND ((? IS NOT NULL AND normalized_email = ?)
                OR (? IS NOT NULL AND normalized_phone = ?))
            """,
        Integer.class,
        organizationId,
        normalizedEmail,
        normalizedEmail,
        normalizedPhone,
        normalizedPhone);
    return count != null && count > 0;
  }

  private UUID parseOwner(UUID organizationId, String value) {
    if (blank(value) == null) {
      return null;
    }
    UUID ownerId = UUID.fromString(value.trim());
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM users WHERE id = ? AND organization_id = ?",
        Integer.class,
        ownerId,
        organizationId);
    if (count == null || count != 1) {
      throw new IllegalArgumentException("ownerId does not belong to organization");
    }
    return ownerId;
  }

  private void updateCounts(UUID jobId, UUID organizationId, int processed, int succeeded, int failed) {
    jdbc.update(
        "UPDATE import_jobs SET processed = ?, succeeded = ?, failed = ? WHERE id = ? AND organization_id = ?",
        processed,
        succeeded,
        failed,
        jobId,
        organizationId);
  }

  private void finish(
      UUID jobId,
      UUID organizationId,
      String errorMessage,
      int processed,
      int succeeded,
      int failed,
      boolean success) {
    jdbc.update(
        """
            UPDATE import_jobs SET status = ?::import_job_status, processed = ?, succeeded = ?,
              failed = ?, error_message = ?, finished_at = now()
            WHERE id = ? AND organization_id = ?
            """,
        success ? "completed" : "failed",
        processed,
        succeeded,
        failed,
        errorMessage,
        jobId,
        organizationId);
  }

  private Map<String, Integer> indexHeaders(List<String> headers) {
    Map<String, Integer> result = new HashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      result.putIfAbsent(headers.get(i), i);
    }
    return result;
  }

  private List<String> normalizeHeaders(List<String> headers) {
    return headers.stream()
        .map(this::normalizeHeader)
        .toList();
  }

  private String value(List<String> values, Map<String, Integer> columns, String name) {
    Integer index = columns.get(normalizeHeader(name));
    return index == null || index >= values.size() ? null : values.get(index);
  }

  private String normalizeHeader(String value) {
    return value == null
        ? ""
        : value.trim().toLowerCase(Locale.ROOT).replace("_", "");
  }

  private List<String> parseCsvLine(String line) {
    List<String> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char character = line.charAt(i);
      if (character == '"') {
        if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        } else {
          quoted = !quoted;
        }
      } else if (character == ',' && !quoted) {
        result.add(current.toString());
        current.setLength(0);
      } else {
        current.append(character);
      }
    }
    result.add(current.toString());
    return result;
  }

  private Instant parseInstant(String value) {
    return blank(value) == null ? null : Instant.parse(value.trim());
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String normalizeEmail(String value) {
    String normalized = blank(value);
    return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
  }

  private String normalizePhone(String value) {
    String normalized = blank(value);
    return normalized == null ? null : normalized.replaceAll("[\\s-]", "");
  }
}
