package com.minicrm.jobs;

import com.minicrm.leads.LeadContactNormalizer;
import com.minicrm.leads.LeadStatus;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ImportJobWorker {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactionTemplate;

  public ImportJobWorker(JdbcTemplate jdbc, TransactionTemplate transactionTemplate) {
    this.jdbc = jdbc;
    this.transactionTemplate = transactionTemplate;
  }

  @Scheduled(fixedDelayString = "${app.import.poll-ms:5000}")
  public void poll() {
    Job job = transactionTemplate.execute(status -> claimNext());
    if (job != null) {
      process(job);
    }
  }

  private Job claimNext() {
    return jdbc.query("""
        WITH candidate AS (
          SELECT id
          FROM import_jobs
          WHERE status = 'queued'::import_job_status
             OR (status = 'running'::import_job_status
                 AND started_at < now() - interval '15 minutes')
          ORDER BY created_at, id
          FOR UPDATE SKIP LOCKED
          LIMIT 1
        )
        UPDATE import_jobs j
        SET status = 'running'::import_job_status, started_at = now(), finished_at = NULL,
            error_message = NULL
        FROM candidate
        WHERE j.id = candidate.id
        RETURNING j.id, j.organization_id, j.created_by_id, j.storage_key
        """, rs -> rs.next() ? new Job(
        rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
        rs.getObject("created_by_id", UUID.class), Path.of(rs.getString("storage_key"))) : null);
  }

  private void process(Job job) {
    int processed = 0;
    int succeeded = 0;
    int failed = 0;
    String failure = null;
    try {
      if (!Files.exists(job.storageKey())) {
        throw new IOException("import file not found");
      }
      validateFile(job.storageKey());
      try (CSVParser parser = parser(job.storageKey())) {
        for (CSVRecord record : parser) {
          processed++;
          try {
            importRecord(job, record);
            succeeded++;
          } catch (RuntimeException exception) {
            failed++;
          }
          updateCounts(job, processed, succeeded, failed);
        }
      }
    } catch (Exception exception) {
      failure = "导入文件校验或处理失败";
    } finally {
      finish(job, processed, succeeded, failed, failure);
      try {
        Files.deleteIfExists(job.storageKey());
      } catch (IOException ignored) {
        // The DB job is authoritative; cleanup can be retried by operations.
      }
    }
  }

  private void validateHeaders(List<String> headers) {
    boolean source = headers.stream().map(this::canonicalHeader).anyMatch("source"::equals);
    boolean name = headers.stream().map(this::canonicalHeader).anyMatch("name"::equals);
    boolean company = headers.stream().map(this::canonicalHeader).anyMatch("company"::equals);
    if (!source || (!name && !company)) {
      throw new IllegalArgumentException("required headers missing");
    }
  }

  /** Parse the complete file before the first row mutation, then parse again for inserts. */
  private void validateFile(Path storageKey) throws IOException {
    try (CSVParser parser = parser(storageKey)) {
      validateHeaders(parser.getHeaderNames());
      for (CSVRecord ignored : parser) {
        // Iteration forces Commons CSV to validate quoting, delimiters and row structure.
      }
    }
  }

  private CSVParser parser(Path storageKey) throws IOException {
    return CSVParser.parse(storageKey, StandardCharsets.UTF_8,
        CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true).setTrim(true).build());
  }

  private void importRecord(Job job, CSVRecord record) {
    String name = value(record, "name");
    String company = value(record, "company");
    String source = value(record, "source");
    if (LeadContactNormalizer.blank(name) == null
        && LeadContactNormalizer.blank(company) == null) {
      throw new IllegalArgumentException("name/company required");
    }
    if (LeadContactNormalizer.blank(source) == null) {
      throw new IllegalArgumentException("source required");
    }
    String email = LeadContactNormalizer.email(value(record, "email"));
    String phone = LeadContactNormalizer.phone(value(record, "phone"));
    if (email != null || phone != null) {
      Integer duplicate = jdbc.queryForObject("""
          SELECT COUNT(*) FROM leads
          WHERE organization_id = ?
            AND ((?::varchar IS NOT NULL AND normalized_email = ?::varchar)
              OR (?::varchar IS NOT NULL AND normalized_phone = ?::varchar))
          """, Integer.class, job.organizationId(), email, email, phone, phone);
      if (duplicate != null && duplicate > 0) throw new IllegalArgumentException("duplicate lead");
    }
    UUID ownerId = parseOwner(job.organizationId(), value(record, "ownerId"));
    jdbc.update("""
        INSERT INTO leads
          (id, organization_id, name, company, phone, normalized_phone, email, normalized_email,
           source, industry, region, notes, status, owner_id, created_by_id,
           next_follow_up_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::lead_status, ?, ?, ?, now())
        """, UUID.randomUUID(), job.organizationId(), LeadContactNormalizer.blank(name),
        LeadContactNormalizer.blank(company), LeadContactNormalizer.blank(value(record, "phone")), phone,
        LeadContactNormalizer.blank(value(record, "email")), email, source.trim(),
        LeadContactNormalizer.blank(value(record, "industry")), LeadContactNormalizer.blank(value(record, "region")),
        LeadContactNormalizer.blank(value(record, "notes")), LeadStatus.NEW.value(), ownerId, job.createdById(),
        parseInstant(value(record, "nextFollowUpAt")));
  }

  private UUID parseOwner(UUID organizationId, String value) {
    String owner = LeadContactNormalizer.blank(value);
    if (owner == null) return null;
    UUID ownerId = UUID.fromString(owner);
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM users WHERE id = ? AND organization_id = ? AND is_active = true",
        Integer.class, ownerId, organizationId);
    if (count == null || count != 1) throw new IllegalArgumentException("owner does not belong to organization");
    return ownerId;
  }

  private String value(CSVRecord record, String name) {
    for (String header : record.getParser().getHeaderNames()) {
      if (canonicalHeader(name).equals(canonicalHeader(header))) return record.get(header);
    }
    return null;
  }

  String canonicalHeader(String value) {
    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace("_", "");
    return normalized.startsWith("﻿") ? normalized.substring(1) : normalized;
  }

  private Instant parseInstant(String value) {
    String trimmed = LeadContactNormalizer.blank(value);
    return trimmed == null ? null : Instant.parse(trimmed);
  }

  private void updateCounts(Job job, int processed, int succeeded, int failed) {
    jdbc.update("""
        UPDATE import_jobs SET processed = ?, succeeded = ?, failed = ?
        WHERE id = ? AND organization_id = ?
        """, processed, succeeded, failed, job.id(), job.organizationId());
  }

  private void finish(Job job, int processed, int succeeded, int failed, String error) {
    jdbc.update("""
        UPDATE import_jobs SET status = ?::import_job_status, processed = ?, succeeded = ?, failed = ?,
          error_message = ?, finished_at = now()
        WHERE id = ? AND organization_id = ?
        """, error == null ? "completed" : "failed", processed, succeeded, failed, error,
        job.id(), job.organizationId());
  }

  private record Job(UUID id, UUID organizationId, UUID createdById, Path storageKey) {}
}
