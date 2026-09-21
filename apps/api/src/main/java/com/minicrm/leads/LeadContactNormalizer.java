package com.minicrm.leads;

import com.minicrm.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

public final class LeadContactNormalizer {
  private LeadContactNormalizer() {}

  public static String email(String value) {
    String trimmed = blank(value);
    return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
  }

  public static String phone(String value) {
    String trimmed = blank(value);
    if (trimmed == null) {
      return null;
    }
    String normalized = trimmed.replaceAll("[\\s\\-()]", "");
    if (!normalized.matches("\\+?\\d{7,15}")) {
      throw new ApiException("VALIDATION_FAILED", "手机号格式无效", HttpStatus.BAD_REQUEST);
    }
    return normalized;
  }

  public static String blank(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }
}
