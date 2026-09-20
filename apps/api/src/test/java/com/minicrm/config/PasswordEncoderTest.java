package com.minicrm.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {
  private final PasswordEncoder passwordEncoder = new SecurityConfig().passwordEncoder();

  @Test
  void hashesAndVerifiesPasswordsWithArgon2() {
    String password = "MiniCrm-Dev-Only-ChangeMe";
    String hash = passwordEncoder.encode(password);

    assertThat(hash).startsWith("$argon2");
    assertThat(passwordEncoder.matches(password, hash)).isTrue();
    assertThat(passwordEncoder.matches("wrong-password", hash)).isFalse();
  }
}
