package com.minicrm.users;

import com.minicrm.common.ApiEnvelope;
import com.minicrm.common.CurrentUser;
import com.minicrm.common.PageSupport;
import com.minicrm.common.RoleCode;
import com.minicrm.common.Roles;
import com.minicrm.common.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  @GetMapping("/me")
  public ApiEnvelope<SecurityUser> me(Authentication authentication) {
    return ApiEnvelope.ok(CurrentUser.require(authentication));
  }

  @PatchMapping("/me")
  public ApiEnvelope<Map<String, Object>> updateMe(
      Authentication authentication,
      @Valid @RequestBody ProfileRequest request,
      HttpServletRequest httpRequest) {
    return ApiEnvelope.ok(service.updateMe(
        CurrentUser.require(authentication),
        new UserService.ProfileRequest(request.name(), request.email(), request.phone()),
        httpRequest));
  }

  @GetMapping
  @Roles({RoleCode.OWNER, RoleCode.ADMIN})
  public ApiEnvelope<List<Map<String, Object>>> list(
      Authentication authentication,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Boolean isActive,
      @RequestParam(required = false) String role) {
    var result = service.list(
        CurrentUser.require(authentication),
        page,
        pageSize,
        keyword,
        isActive,
        role);
    return ApiEnvelope.ok(result.items(), result.meta());
  }

  @PostMapping
  @Roles({RoleCode.OWNER, RoleCode.ADMIN})
  public ApiEnvelope<Map<String, Object>> create(
      Authentication authentication,
      @Valid @RequestBody CreateUserRequest request,
      HttpServletRequest httpRequest) {
    return ApiEnvelope.ok(service.create(
        CurrentUser.require(authentication),
        new UserService.CreateUserRequest(
            request.name(), request.email(), request.phone(), request.password(), request.roleCodes()),
        httpRequest));
  }

  @PatchMapping("/{id}/status")
  @Roles({RoleCode.OWNER, RoleCode.ADMIN})
  public ApiEnvelope<Map<String, Object>> status(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody StatusRequest request,
      HttpServletRequest httpRequest) {
    return ApiEnvelope.ok(service.updateStatus(
        CurrentUser.require(authentication), id, request.isActive(), httpRequest));
  }

  @PatchMapping("/{id}")
  @Roles({RoleCode.OWNER, RoleCode.ADMIN})
  public ApiEnvelope<Map<String, Object>> update(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateUserRequest request,
      HttpServletRequest httpRequest) {
    return ApiEnvelope.ok(service.update(
        CurrentUser.require(authentication),
        id,
        new UserService.UpdateUserRequest(
            request.name(),
            request.email(),
            request.phone(),
            request.roleCodes(),
            request.isActive()),
        httpRequest));
  }

  public record ProfileRequest(
      @Size(min = 1, max = 80) String name,
      @Email @Size(max = 254) String email,
      @Size(max = 32) String phone) {}

  public record CreateUserRequest(
      @NotBlank @Size(max = 80) String name,
      @Email @Size(max = 254) String email,
      @Size(max = 32) String phone,
      @NotBlank @Size(min = 8, max = 128) String password,
      @NotEmpty List<@NotBlank String> roleCodes) {}

  public record StatusRequest(@NotNull Boolean isActive) {}

  public record UpdateUserRequest(
      @Size(max = 80) String name,
      @Email @Size(max = 254) String email,
      @Size(max = 32) String phone,
      List<@NotBlank String> roleCodes,
      Boolean isActive) {}
}
