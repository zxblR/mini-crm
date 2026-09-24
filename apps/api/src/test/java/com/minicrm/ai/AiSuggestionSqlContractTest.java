package com.minicrm.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AiSuggestionSqlContractTest {
  @Test
  void migrationDeclaresAiSuggestionTableAndCacheKey() throws Exception {
    Path migration = Path.of("../../prisma/migrations/20260924000000_ai_suggestions/migration.sql");
    String sql = Files.readString(migration);

    assertTrue(sql.contains("CREATE TABLE ai_suggestions"));
    assertTrue(sql.contains("CONSTRAINT ai_suggestions_cache_key UNIQUE"));
    assertTrue(sql.contains("created_at TIMESTAMPTZ NOT NULL"));
    assertTrue(sql.contains("updated_at TIMESTAMPTZ NOT NULL"));
  }
}
