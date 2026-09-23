package com.minicrm.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiEnvelope<Object>> handleApi(ApiException exception, HttpServletRequest request) {
    log.warn("API request failed, code={}, requestId={}, path={}",
        exception.code(), requestId(request), request.getRequestURI());
    return ResponseEntity.status(exception.status()).body(ApiEnvelope.failure(
        exception.code(), exception.getMessage(), exception.details(), requestId(request)));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiEnvelope<Object>> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
    log.warn("API request failed, code={}, requestId={}, path={}",
        "VALIDATION_FAILED", requestId(request), request.getRequestURI());
    return ResponseEntity.badRequest().body(ApiEnvelope.failure(
        "VALIDATION_FAILED", "请求参数校验失败", requestId(request)));
  }

  @ExceptionHandler({
      HttpMessageNotReadableException.class,
      MethodArgumentTypeMismatchException.class,
      MissingServletRequestParameterException.class,
      MissingServletRequestPartException.class,
      ConstraintViolationException.class
  })
  public ResponseEntity<ApiEnvelope<Object>> handleMalformedRequest(Exception exception, HttpServletRequest request) {
    log.warn("API request failed, code={}, requestId={}, path={}",
        "VALIDATION_FAILED", requestId(request), request.getRequestURI());
    return ResponseEntity.badRequest().body(ApiEnvelope.failure(
        "VALIDATION_FAILED", "请求参数校验失败", requestId(request)));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiEnvelope<Object>> handleUnexpected(Exception exception, HttpServletRequest request) {
    log.error("Unhandled exception, requestId={}, path={}",
        requestId(request), request.getRequestURI(), exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiEnvelope.failure(
        "INTERNAL_ERROR", "服务内部错误", requestId(request)));
  }

  private String requestId(HttpServletRequest request) {
    Object value = request.getAttribute(RequestIdFilter.REQUEST_ID);
    return value == null ? "" : value.toString();
  }
}
