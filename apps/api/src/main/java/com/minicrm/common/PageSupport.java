package com.minicrm.common;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

public final class PageSupport {
  private PageSupport() {}

  public static PageRequest request(Integer page, Integer pageSize) {
    int normalizedPage = page == null ? 1 : page;
    int normalizedSize = pageSize == null ? 20 : pageSize;
    if (normalizedPage < 1 || normalizedSize < 1 || normalizedSize > 100) {
      throw new ApiException("VALIDATION_FAILED", "分页参数无效", HttpStatus.BAD_REQUEST);
    }
    return new PageRequest(normalizedPage, normalizedSize, (long) (normalizedPage - 1) * normalizedSize);
  }

  public static ApiEnvelope.ApiMeta meta(PageRequest page, long total) {
    return new ApiEnvelope.ApiMeta(null, page.page(), page.pageSize(), total, null, null, null);
  }

  public record PageRequest(int page, int pageSize, long offset) {}

  public record Result<T>(List<T> items, ApiEnvelope.ApiMeta meta) {}

  public static Map<String, Object> result(List<?> items, int page, int pageSize, long total) {
    return Map.of("items", items, "page", page, "pageSize", pageSize, "total", total);
  }
}
