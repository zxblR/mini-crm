package com.minicrm.leads;

import java.util.Arrays;

public enum LeadStatus {
  NEW("new"),
  CONTACTED("contacted"),
  QUALIFIED("qualified"),
  PROPOSAL("proposal"),
  NEGOTIATION("negotiation"),
  WON("won"),
  LOST("lost");

  private final String value;

  LeadStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean terminal() {
    return this == WON || this == LOST;
  }

  public static LeadStatus fromValue(String value) {
    return Arrays.stream(values())
        .filter(status -> status.value.equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown lead status: " + value));
  }
}
