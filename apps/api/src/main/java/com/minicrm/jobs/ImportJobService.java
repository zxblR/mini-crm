package com.minicrm.jobs;

import com.minicrm.common.ActivityLogService;
import com.minicrm.common.ApiException;
import com.minicrm.common.BusinessRules;
import com.minicrm.common.ImportJobStatus;
import com.minicrm.common.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ImportJobService {
  private final JdbcTemplate jdbc;
  private final Path storageDirectory;
  private final ActivityLogService activityLogService;

  @Autowired
  public ImportJobService(
      JdbcTemplate jdbc,
      @Value("${app.import.storage-dir:./data/imports}") String storageDirectory,
      ActivityLogService activityLogService) {
    this.jdbc = jdbc;
    this.storageDirectory = Paths.get(storageDirectory).toAbsolutePath().normalize();
    this.activityLogService = activityLogService;
  }

  public ImportJobService(JdbcTemplate jdbc, String storageDirectory) {
    this(jdbc, storageDirectory, null);
  }

  @Transactional
  public Map<String, Object> create(
      SecurityUser actor,
      MultipartFile file,
      HttpServletRequest request) {
    BusinessRules.requireAdmin(actor);
    String originalName = file == null ? null : file.getOriginalFilename();
    if (file == null
        || file.isEmpty()
        || file.getSize() > 10 * 1024 * 1024
        || originalName == null
        || !originalName.toLowerCase().endsWith(".csv")) {
      throw new ApiException(
          "IMPORT_INVALID_FILE",
          "仅支持不超过 10MB 的 CSV 文件",
          HttpStatus.BAD_REQUEST);
    }

    UUID id = UUID.randomUUID();
    String safeName = Paths.get(originalName).getFileName().toString();
    Path destination = storageDirectory.resolve(id + "-" + safeName).normalize();
    if (!destination.startsWith(storageDirectory)) {
      throw new ApiException(
          "IMPORT_INVALID_FILE",
          "文件名无效",
          HttpStatus.BAD_REQUEST);
    }
    try {
      Files.createDirectories(storageDirectory);
      file.transferTo(destination);
    } catch (IOException exception) {
      throw new ApiException(
          "IMPORT_INVALID_FILE",
          "导入文件无法保存",
          HttpStatus.BAD_REQUEST);
    }

    jdbc.update(
        """
            INSERT INTO import_jobs
              (id, organization_id, created_by_id, type, status, file_name, storage_key)
            VALUES (?, ?, ?, 'lead_import', ?::import_job_status, ?, ?)
            """,
        id,
        actor.organizationId(),
        actor.id(),
        ImportJobStatus.queued.name(),
        safeName,
        destination.toString());
    if (activityLogService != null) {
      activityLogService.record(actor, "IMPORT_LEADS", "IMPORT_JOB", id, null, request);
    }
    return Map.of("jobId", id, "status", ImportJobStatus.queued.name());
  }

  public Map<String, Object> get(SecurityUser actor, UUID id) {
    Map<String, Object> job = jdbc.query(
        """
            SELECT id, organization_id, created_by_id, type, status, processed, succeeded,
                   failed, error_file_url, created_at, finished_at
            FROM import_jobs
            WHERE id = ? AND organization_id = ?
            """,
        rs -> rs.next() ? row(rs) : null,
        id,
        actor.organizationId());
    if (job == null
        || (!BusinessRules.isAdmin(actor) && !actor.id().equals(job.get("createdById")))) {
      throw new ApiException(
          "RESOURCE_NOT_FOUND",
          "导入任务不存在",
          HttpStatus.NOT_FOUND);
    }
    return job;
  }

  private Map<String, Object> row(java.sql.ResultSet rs) throws java.sql.SQLException {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", rs.getObject("id"));
    result.put("createdById", rs.getObject("created_by_id"));
    result.put("type", rs.getString("type"));
    result.put("status", rs.getString("status"));
    result.put("processed", rs.getInt("processed"));
    result.put("succeeded", rs.getInt("succeeded"));
    result.put("failed", rs.getInt("failed"));
    result.put("errorFileUrl", rs.getString("error_file_url"));
    result.put("createdAt", rs.getObject("created_at"));
    result.put("finishedAt", rs.getObject("finished_at"));
    return result;
  }
}
