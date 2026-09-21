package com.minicrm.leads;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExportServiceTest {
  @Test
  void escapesSpreadsheetFormulaPrefixesAfterLeadingWhitespace() {
    assertThat(ExportService.escapeCell("  =SUM(A1:A2)")).isEqualTo("'  =SUM(A1:A2)");
    assertThat(ExportService.escapeCell("+447700900123")).isEqualTo("'+447700900123");
    assertThat(ExportService.escapeCell("ordinary value")).isEqualTo("ordinary value");
    assertThat(ExportService.escapeCell(null)).isEmpty();
  }
}
