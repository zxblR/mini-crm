package com.minicrm.teams;

import com.minicrm.common.ApiEnvelope;
import com.minicrm.common.CurrentUser;
import com.minicrm.common.RoleCode;
import com.minicrm.common.Roles;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/teams/current")
public class TeamController {
  private final TeamService service;

  public TeamController(TeamService service) {
    this.service = service;
  }

  @GetMapping
  public ApiEnvelope<Map<String, Object>> current(Authentication authentication) {
    return ApiEnvelope.ok(service.current(CurrentUser.require(authentication)));
  }

  @PatchMapping
  @Roles(RoleCode.OWNER)
  public ApiEnvelope<Map<String, Object>> update(
      Authentication authentication,
      @Valid @RequestBody TeamService.UpdateTeamRequest request,
      HttpServletRequest httpRequest) {
    return ApiEnvelope.ok(service.update(
        CurrentUser.require(authentication), request, httpRequest));
  }
}
