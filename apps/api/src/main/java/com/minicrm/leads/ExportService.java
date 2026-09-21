package com.minicrm.leads;

import com.minicrm.common.ApiException;
import com.minicrm.common.ActivityLogService;
import com.minicrm.common.BusinessRules;
import com.minicrm.common.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class ExportService {
  private static final int MAX_ROWS = 10_000;
  private final JdbcTemplate jdbc;
  private final ActivityLogService activityLogService;

  @Autowired
  public ExportService(JdbcTemplate jdbc, ActivityLogService activityLogService) {
    this.jdbc = jdbc;
    this.activityLogService = activityLogService;
  }

  public ExportService(JdbcTemplate jdbc) {
    this(jdbc, null);
  }

  public Export export(SecurityUser actor, String status, String ownerId, String keyword) {
    return export(actor, status, ownerId, keyword, null, null, null, null, null);
  }

  @Transactional
  public Export export(SecurityUser actor, String status, String ownerId, String keyword,
                       String source, String from, String to, Boolean archived,
                       HttpServletRequest request) {
    BusinessRules.requireAdmin(actor);
    LeadStatus parsedStatus = null;
    if (status != null && !status.isBlank()) {
      try {
        parsedStatus = LeadStatus.fromValue(status);
      } catch (IllegalArgumentException exception) {
        throw new ApiException("VALIDATION_FAILED", "线索状态无效", HttpStatus.BAD_REQUEST);
      }
    }
    UUID parsedOwner = null;
    if (ownerId != null && !ownerId.isBlank()) {
      try {
        parsedOwner = UUID.fromString(ownerId);
      } catch (IllegalArgumentException exception) {
        throw new ApiException("VALIDATION_FAILED", "负责人 ID 无效", HttpStatus.BAD_REQUEST);
      }
    }
    StringBuilder where = new StringBuilder(" WHERE l.organization_id = ? ");
    java.util.List<Object> args = new java.util.ArrayList<>();
    args.add(actor.organizationId());
    if (Boolean.TRUE.equals(archived)) where.append(" AND l.archived_at IS NOT NULL ");
    else where.append(" AND l.archived_at IS NULL ");
    if (parsedStatus != null) { where.append(" AND l.status = ?::lead_status "); args.add(parsedStatus.value()); }
    if (parsedOwner != null) { where.append(" AND l.owner_id = ? "); args.add(parsedOwner); }
    if (keyword != null && !keyword.isBlank()) {
      where.append(" AND (LOWER(COALESCE(l.name, '')) LIKE ? OR LOWER(COALESCE(l.company, '')) LIKE ? "
          + "OR LOWER(COALESCE(l.email, '')) LIKE ? OR COALESCE(l.phone, '') LIKE ?) ");
      String value = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
      args.add(value); args.add(value); args.add(value); args.add(value);
    }
    if (source != null && !source.isBlank()) {
      where.append(" AND l.source = ? ");
      args.add(source.trim());
    }
    if (from != null && !from.isBlank()) {
      where.append(" AND l.created_at >= ?::timestamptz ");
      args.add(parseInstant(from, "from"));
    }
    if (to != null && !to.isBlank()) {
      where.append(" AND l.created_at <= ?::timestamptz ");
      args.add(parseInstant(to, "to"));
    }
    Long count = jdbc.queryForObject("SELECT COUNT(*) FROM leads l" + where, Long.class, args.toArray());
    if (count != null && count > MAX_ROWS) {
      throw new ApiException("VALIDATION_FAILED", "导出行数超过上限", HttpStatus.BAD_REQUEST);
    }
    if (activityLogService != null) {
      activityLogService.record(actor, "EXPORT_LEADS", "LEAD", null,
          java.util.Map.of("rowCount", count == null ? 0L : count), request);
    }
    java.util.List<Object> streamArgs = new java.util.ArrayList<>(args);
    return new Export("leads-" + Instant.now().toString().replaceAll("[^0-9A-Za-z]", "") + ".csv",
        output -> write(output, "SELECT l.id, l.name, l.company, l.phone, l.email, l.source, l.status, "
            + "l.owner_id, l.created_at, l.updated_at FROM leads l" + where
            + " ORDER BY l.updated_at DESC, l.id LIMIT " + MAX_ROWS, streamArgs));
  }

  private Instant parseInstant(String value, String field) {
    try {
      return Instant.parse(value);
    } catch (RuntimeException exception) {
      throw new ApiException("VALIDATION_FAILED", field + " 日期格式无效", HttpStatus.BAD_REQUEST);
    }
  }

  private void write(java.io.OutputStream output, String sql, java.util.List<Object> args) throws IOException {
    try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(output, StandardCharsets.UTF_8),
        CSVFormat.DEFAULT.builder().setHeader("id", "name", "company", "phone", "email", "source", "status",
            "ownerId", "createdAt", "updatedAt").build())) {
      jdbc.query(sql, args.toArray(), rs -> {
        try {
          printer.printRecord(escapeCell(rs.getObject("id")), escapeCell(rs.getString("name")),
              escapeCell(rs.getString("company")), escapeCell(rs.getString("phone")), escapeCell(rs.getString("email")),
              escapeCell(rs.getString("source")), escapeCell(rs.getString("status")), escapeCell(rs.getObject("owner_id")),
              escapeCell(rs.getObject("created_at")), escapeCell(rs.getObject("updated_at")));
        } catch (IOException exception) {
          throw new ExportIOException(exception);
        }
      });
    } catch (ExportIOException exception) {
      throw exception.ioException;
    }
  }

  static String escapeCell(Object value) {
    if (value == null) return "";
    String text = String.valueOf(value);
    String trimmed = text.stripLeading();
    return trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("-") || trimmed.startsWith("@")
        ? "'" + text : text;
  }

  public record Export(String filename, StreamingResponseBody body) {}

  private static class ExportIOException extends RuntimeException {
    private final IOException ioException;
    private ExportIOException(IOException ioException) { this.ioException = ioException; }
  }
}
