package com.minicrm.jobs;

import org.junit.jupiter.api.Test;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ImportJobWorkerTest {
  private final ImportJobWorker worker = new ImportJobWorker(
      mock(JdbcTemplate.class), mock(TransactionTemplate.class));

  @Test
  void canonicalizesCsvHeadersAcrossBomSnakeCaseAndCamelCase() {
    assertThat(worker.canonicalHeader("\ufeffowner_id")).isEqualTo("ownerid");
    assertThat(worker.canonicalHeader("ownerId")).isEqualTo("ownerid");
    assertThat(worker.canonicalHeader(" Source ")).isEqualTo("source");
  }

  @Test
  void importBindsNextFollowUpAtAsOffsetDateTime() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ImportJobWorker worker = new ImportJobWorker(jdbc, mock(TransactionTemplate.class));
    UUID jobId = UUID.randomUUID();
    UUID organizationId = UUID.randomUUID();
    UUID creatorId = UUID.randomUUID();
    Class<?> jobType = Class.forName("com.minicrm.jobs.ImportJobWorker$Job");
    Constructor<?> constructor = jobType.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    Object job = constructor.newInstance(jobId, organizationId, creatorId, java.nio.file.Path.of("unused.csv"));
    Instant dueAt = Instant.parse("2026-09-30T12:00:00Z");
    String csv = "name,company,source,nextFollowUpAt\nLead,Acme,web," + dueAt + "\n";

    try (CSVParser parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT.builder()
        .setHeader().setSkipHeaderRecord(true).setTrim(true).build())) {
      Method importRecord = ImportJobWorker.class.getDeclaredMethod(
          "importRecord", jobType, org.apache.commons.csv.CSVRecord.class);
      importRecord.setAccessible(true);
      importRecord.invoke(worker, job, parser.iterator().next());
    }

    org.mockito.ArgumentCaptor<Object[]> args = org.mockito.ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(anyString(), args.capture());
    assertThat(args.getValue()).contains(OffsetDateTime.ofInstant(dueAt, java.time.ZoneOffset.UTC));
  }
}
