package com.minicrm.ai.dto;

import java.util.List;

public record AiSuggestionPageDTO(List<AiSuggestionDTO> items, int page, int pageSize, long total) {}
