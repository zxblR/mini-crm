package com.minicrm.teams;

import com.minicrm.common.ActivityLogService;
import com.minicrm.common.ApiException;
import com.minicrm.common.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TeamService {
  private final JdbcTemplate jdbc;
  private final ActivityLogService activityLogService;

  public TeamService(JdbcTemplate jdbc, ActivityLogService activityLogService) {
    this.jdbc = jdbc;
    this.activityLogService = activityLogService;
  }

  public Map<String, Object> current(SecurityUser actor) {
    Map<String, Object> team = jdbc.query("""
        SELECT id, name, slug, timezone FROM organizations WHERE id = ?
        """, rs -> rs.next() ? team(rs) : null, actor.organizationId());
    if (team == null) {
      throw new ApiException("RESOURCE_NOT_FOUND", "团队不存在", HttpStatus.NOT_FOUND);
    }
    return team;
  }

  public Map<String, Object> update(
      SecurityUser actor,
      UpdateTeamRequest request,
      HttpServletRequest httpRequest) {
    int count = jdbc.update("""
        UPDATE organizations SET name = COALESCE(?, name), timezone = COALESCE(?, timezone), updated_at = now()
        WHERE id = ?
        """, blankToNull(request.name()), blankToNull(request.timezone()), actor.organizationId());
    if (count == 0) {
      throw new ApiException("TEAM_NOT_FOUND", "团队不存在", HttpStatus.NOT_FOUND);
    }
    activityLogService.record(actor, "UPDATE_TEAM", "TEAM", actor.organizationId(), null, httpRequest);
    return current(actor);
  }

  private Map<String, Object> team(java.sql.ResultSet rs) throws java.sql.SQLException {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", rs.getObject("id"));
    result.put("name", rs.getString("name"));
    result.put("slug", rs.getString("slug"));
    result.put("timezone", rs.getString("timezone"));
    return result;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record UpdateTeamRequest(
      @jakarta.validation.constraints.Size(min = 1, max = 120) String name,
      @jakarta.validation.constraints.Size(max = 64) String timezone) {}
}
