package com.minicrm.common;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ApiException extends RuntimeException {
  private final String code;
  private final HttpStatus status;
  private final Map<String, Object> details;

  public ApiException(String code, String message, HttpStatus status) {
    this(code, message, Map.of(), status);
  }

  public ApiException(String code, String message, Map<String, Object> details, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status;
    this.details = details == null ? Map.of() : Map.copyOf(details);
  }

  public String code() { return code; }
  public HttpStatus status() { return status; }
  public Map<String, Object> details() { return details; }
}
