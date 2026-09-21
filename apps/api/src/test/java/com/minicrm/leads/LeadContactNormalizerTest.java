package com.minicrm.leads;

import com.minicrm.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeadContactNormalizerTest {
  @Test
  void normalizesEmailWithRootLocaleAndTrim() {
    assertThat(LeadContactNormalizer.email("  SALES@Example.COM  "))
        .isEqualTo("sales@example.com");
    assertThat(LeadContactNormalizer.email("  ")).isNull();
  }

  @Test
  void normalizesPhoneSeparatorsAndKeepsLeadingPlus() {
    assertThat(LeadContactNormalizer.phone(" +86 (138) 0011-2233 "))
        .isEqualTo("+8613800112233");
  }

  @Test
  void rejectsInvalidPhoneLengthOrCharacters() {
    assertThatThrownBy(() -> LeadContactNormalizer.phone("123-abc"))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("VALIDATION_FAILED");
    assertThatThrownBy(() -> LeadContactNormalizer.phone("123456"))
        .isInstanceOf(ApiException.class);
  }
}
