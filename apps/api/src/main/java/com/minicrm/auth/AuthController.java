package com.minicrm.auth;

import com.minicrm.common.ApiEnvelope;
import com.minicrm.common.CurrentUser;
import com.minicrm.common.Public;
import com.minicrm.common.SecurityUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  @Public
  public ApiEnvelope<Map<String, Object>> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest) {
    return ApiEnvelope.ok(authService.login(request.account(), request.password(), httpRequest));
  }

  @PostMapping("/refresh")
  @Public
  public ApiEnvelope<Map<String, Object>> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
    return ApiEnvelope.ok(authService.refresh(request.refreshToken(), httpRequest));
  }

  @PostMapping("/logout")
  public ApiEnvelope<Map<String, Object>> logout(Authentication authentication, @Valid @RequestBody RefreshRequest request) {
    return ApiEnvelope.ok(authService.logout(CurrentUser.require(authentication), request.refreshToken()));
  }

  @GetMapping("/me")
  public ApiEnvelope<SecurityUser> me(Authentication authentication) {
    return ApiEnvelope.ok(CurrentUser.require(authentication));
  }

  public record LoginRequest(
      @NotBlank @Size(min = 3, max = 254) String account,
      @NotBlank @Size(min = 8, max = 128) String password) {}

  public record RefreshRequest(@NotBlank @Size(min = 20, max = 256) String refreshToken) {}
}
