package com.minicrm.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
  public static final String REQUEST_ID = "requestId";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();
    request.setAttribute(REQUEST_ID, requestId);
    response.setHeader("X-Request-Id", requestId);
    chain.doFilter(request, response);
  }
}
