package com.minicrm.ai;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leads/{leadId}/ai")
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/intent-score")
    public AiDtos.Envelope<AiDtos.SuggestionResponse> intentScore(@PathVariable UUID leadId,
                                                                   @Valid @RequestBody AiDtos.GenerateRequest request,
                                                                   Authentication authentication,
                                                                   @RequestHeader("X-Organization-Id") String organizationId) {
        return generate(leadId, AiSuggestionType.INTENT_SCORE, request, authentication, organizationId);
    }

    @PostMapping("/follow-up-summary")
    public AiDtos.Envelope<AiDtos.SuggestionResponse> followUpSummary(@PathVariable UUID leadId,
                                                                       @Valid @RequestBody AiDtos.GenerateRequest request,
                                                                       Authentication authentication,
                                                                       @RequestHeader("X-Organization-Id") String organizationId) {
        return generate(leadId, AiSuggestionType.FOLLOW_UP_SUMMARY, request, authentication, organizationId);
    }

    @PostMapping("/next-action")
    public AiDtos.Envelope<AiDtos.SuggestionResponse> nextAction(@PathVariable UUID leadId,
                                                                  @Valid @RequestBody AiDtos.GenerateRequest request,
                                                                  Authentication authentication,
                                                                  @RequestHeader("X-Organization-Id") String organizationId) {
        return generate(leadId, AiSuggestionType.NEXT_ACTION, request, authentication, organizationId);
    }

    @PostMapping("/script")
    public AiDtos.Envelope<AiDtos.SuggestionResponse> script(@PathVariable UUID leadId,
                                                              @Valid @RequestBody AiDtos.GenerateRequest request,
                                                              Authentication authentication,
                                                              @RequestHeader("X-Organization-Id") String organizationId) {
        return generate(leadId, AiSuggestionType.SCRIPT, request, authentication, organizationId);
    }

    @PostMapping("/wake-up")
    public AiDtos.Envelope<AiDtos.SuggestionResponse> wakeUp(@PathVariable UUID leadId,
                                                              @Valid @RequestBody AiDtos.GenerateRequest request,
                                                              Authentication authentication,
                                                              @RequestHeader("X-Organization-Id") String organizationId) {
        return generate(leadId, AiSuggestionType.WAKE_UP, request, authentication, organizationId);
    }

    @GetMapping("/{type}")
    public AiDtos.Envelope<AiDtos.SuggestionResponse> read(@PathVariable UUID leadId,
                                                            @PathVariable AiSuggestionType type,
                                                            Authentication authentication,
                                                            @RequestHeader("X-Organization-Id") String organizationId) {
        return AiDtos.Envelope.ok(aiService.read(leadId, type, authentication, organizationId), Map.of());
    }

    private AiDtos.Envelope<AiDtos.SuggestionResponse> generate(UUID leadId, AiSuggestionType type,
                                                                  AiDtos.GenerateRequest request,
                                                                  Authentication authentication,
                                                                  String organizationId) {
        return AiDtos.Envelope.ok(aiService.generate(leadId, type, request.forceRefresh(), authentication, organizationId), Map.of());
    }
}
