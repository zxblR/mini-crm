package com.minicrm.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final AuthenticationGuard authenticationGuard;
  private final RolesGuard rolesGuard;

  public WebConfig(AuthenticationGuard authenticationGuard, RolesGuard rolesGuard) {
    this.authenticationGuard = authenticationGuard;
    this.rolesGuard = rolesGuard;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authenticationGuard)
        .excludePathPatterns(
            "/api/docs",
            "/api/docs/**",
            "/api/docs-json",
            "/api/docs-json/**");
    registry.addInterceptor(rolesGuard);
  }
}
