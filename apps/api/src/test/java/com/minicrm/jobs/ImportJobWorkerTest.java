package com.minicrm.jobs;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ImportJobWorkerTest {
  private final ImportJobWorker worker = new ImportJobWorker(
      mock(JdbcTemplate.class), mock(TransactionTemplate.class));

  @Test
  void canonicalizesCsvHeadersAcrossBomSnakeCaseAndCamelCase() {
    assertThat(worker.canonicalHeader("\ufeffowner_id")).isEqualTo("ownerid");
    assertThat(worker.canonicalHeader("ownerId")).isEqualTo("ownerid");
    assertThat(worker.canonicalHeader(" Source ")).isEqualTo("source");
  }
}
