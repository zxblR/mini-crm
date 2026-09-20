package com.minicrm.auth;

import com.minicrm.common.ApiEnvelope;
import com.minicrm.common.CurrentUser;
import com.minicrm.common.SecurityUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api", "/api/v1"})
public class MeController {
  @GetMapping("/me")
  public ApiEnvelope<SecurityUser> me(Authentication authentication) {
    return ApiEnvelope.ok(CurrentUser.require(authentication));
  }
}
