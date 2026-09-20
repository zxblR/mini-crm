package com.minicrm.auth;

import com.minicrm.common.SecurityUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwtService;
  private final AuthUserService authUserService;

  public JwtAuthenticationFilter(JwtService jwtService, AuthUserService authUserService) {
    this.jwtService = jwtService;
    this.authUserService = authUserService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      try {
        Claims claims = jwtService.parse(header.substring(7));
        UUID userId = UUID.fromString(claims.getSubject());
        SecurityUser user = authUserService.findActiveUser(userId);
        if (user != null) {
          var authentication = new UsernamePasswordAuthenticationToken(user, null, roles(user.roles()));
          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      } catch (RuntimeException ignored) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }

  private List<org.springframework.security.core.authority.SimpleGrantedAuthority> roles(List<String> roles) {
    return roles.stream().map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)).toList();
  }
}
