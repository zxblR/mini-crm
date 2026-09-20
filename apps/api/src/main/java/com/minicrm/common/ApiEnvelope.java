package com.minicrm.common;

import java.util.Map;

public record ApiEnvelope<T>(T data, ApiMeta meta, Object error) {
  public static <T> ApiEnvelope<T> ok(T data) {
    return new ApiEnvelope<>(data, new ApiMeta(null), null);
  }

  public static <T> ApiEnvelope<T> ok(T data, ApiMeta meta) {
    return new ApiEnvelope<>(data, meta, null);
  }

  public ApiEnvelope<T> withRequestId(String requestId) {
    return new ApiEnvelope<>(data, meta.withRequestId(requestId), error);
  }

  public record ApiMeta(
      String requestId,
      Integer page,
      Integer pageSize,
      Long total,
      String from,
      String to,
      String timezone) {
    public ApiMeta(String requestId) {
      this(requestId, null, null, null, null, null, null);
    }

    public ApiMeta withRequestId(String value) {
      return new ApiMeta(value, page, pageSize, total, from, to, timezone);
    }

    public static ApiMeta page(String requestId, int page, int pageSize, long total) {
      return new ApiMeta(requestId, page, pageSize, total, null, null, null);
    }

    public static ApiMeta range(String requestId, String from, String to, String timezone) {
      return new ApiMeta(requestId, null, null, null, from, to, timezone);
    }
  }

  public static <T> ApiEnvelope<T> failure(String code, String message, String requestId) {
    return failure(code, message, Map.of(), requestId);
  }

  public static <T> ApiEnvelope<T> failure(
      String code, String message, Map<String, Object> details, String requestId) {
    return new ApiEnvelope<>(null, new ApiMeta(requestId), new ApiError(code, message, details));
  }

  public record ApiError(String code, String message, Map<String, Object> details) {
    public ApiError(String code, String message) {
      this(code, message, Map.of());
    }
  }
}
