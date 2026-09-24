package com.minicrm.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AiScriptRequest(
    @NotBlank @Pattern(regexp = "new_lead|price_sensitive|existing_customer|decision_maker|unknown") String customerType,
    @NotBlank @Pattern(regexp = "call|wechat|email") String channel,
    @NotBlank @Size(max = 300) String objective,
    @Size(max = 300) String objection,
    boolean forceRefresh) {}
