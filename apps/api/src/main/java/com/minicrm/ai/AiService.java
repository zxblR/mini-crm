package com.minicrm.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicrm.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AiService {
    private static final String PROMPT_VERSION = "v1";
    private final AiSuggestionRepository repository;
    private final AiClient aiClient;
    private final AiActorResolver actorResolver;
    private final ObjectMapper objectMapper;
    private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiService.class);

    public AiService(AiSuggestionRepository repository, AiClient aiClient, AiActorResolver actorResolver,
                     ObjectMapper objectMapper) {
        this.repository = repository;
        this.aiClient = aiClient;
        this.actorResolver = actorResolver;
        this.objectMapper = objectMapper;
    }

    public AiDtos.SuggestionResponse generate(UUID leadId, AiSuggestionType type, boolean forceRefresh,
                                              Authentication authentication, String organizationId) {
        AiDtos.AiActor actor = actorResolver.resolve(authentication, organizationId);
        AiDtos.LeadContext context = repository.findLeadContext(actor.organizationId(), leadId)
                .orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND", "线索不存在", HttpStatus.NOT_FOUND));
        if (!actor.canTrigger()) {
            throw new ApiException("FORBIDDEN", "只读角色不能触发 AI", HttpStatus.FORBIDDEN);
        }
        Optional<UUID> owner = repository.findOwner(actor.organizationId(), leadId);
        if (actor.sales() && owner.filter(actor.userId()::equals).isEmpty()) {
            throw new ApiException("FORBIDDEN", "只有线索负责人可以触发 AI", HttpStatus.FORBIDDEN);
        }

        String inputHash = hash(context, type);
        if (!forceRefresh) {
            Optional<AiDtos.StoredSuggestion> cached = repository.findFresh(actor.organizationId(), leadId, type,
                    inputHash, PROMPT_VERSION, Instant.now());
            if (cached.isPresent()) {
                return response(cached.get(), true);
            }
        }

        JsonNode result;
        try {
            result = aiClient.generate(type, context);
        } catch (AiClient.AiProviderException exception) {
            log.error("AI provider failed, status={}", exception.status(), exception);
            int providerStatus = exception.status();
            if (providerStatus == 400 || providerStatus == 401 || providerStatus == 403
                    || providerStatus == 404 || providerStatus == 422 || providerStatus == 429) {
                String code = providerStatus == 429 ? "AI_RATE_LIMITED" : "AI_PROVIDER_ERROR";
                String message = providerStatus == 429 ? "AI 限流" : "AI 服务请求失败";
                throw new ApiException(code, message, HttpStatus.valueOf(providerStatus));
            }
            if (providerStatus == 502) {
                throw new ApiException("AI_INVALID_RESPONSE", "AI 返回格式错误", HttpStatus.BAD_GATEWAY);
            }
            throw new ApiException("AI_DEPENDENCY_UNAVAILABLE", "AI 服务不可用", HttpStatus.SERVICE_UNAVAILABLE);
        }
        Instant now = Instant.now();
        AiDtos.StoredSuggestion stored = repository.upsert(actor.organizationId(), actor.userId(), context, type,
                inputHash, PROMPT_VERSION, result, aiClient.model(), now.plus(24, ChronoUnit.HOURS), now);
        log.info("AI_SUGGESTION type={} leadId={} organizationId={} userId={} cached=false",
                type, leadId, actor.organizationId(), actor.userId());
        return response(stored, false);
    }

    public AiDtos.SuggestionResponse read(UUID leadId, AiSuggestionType type, Authentication authentication,
                                          String organizationId) {
        AiDtos.AiActor actor = actorResolver.resolve(authentication, organizationId);
        AiDtos.LeadContext context = repository.findLeadContext(actor.organizationId(), leadId)
                .orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND", "线索不存在", HttpStatus.NOT_FOUND));
        if (!actor.canTrigger() && !actor.support()) {
            throw new ApiException("FORBIDDEN", "无权访问 AI 结果", HttpStatus.FORBIDDEN);
        }
        return latestOrNotFound(actor, context.leadId(), type);
    }

    private AiDtos.SuggestionResponse latestOrNotFound(AiDtos.AiActor actor, UUID leadId, AiSuggestionType type) {
        return repository.findLatest(actor.organizationId(), leadId, type)
                .map(value -> response(value, true))
                .orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND", "AI 建议不存在", HttpStatus.NOT_FOUND));
    }

    private AiDtos.SuggestionResponse response(AiDtos.StoredSuggestion value, boolean cached) {
        return new AiDtos.SuggestionResponse(value.id(), value.leadId(), value.type(), value.result(), value.model(),
                value.promptVersion(), value.expiresAt(), cached);
    }

    private String hash(AiDtos.LeadContext context, AiSuggestionType type) {
        try {
            String canonical = objectMapper.writeValueAsString(Map.of(
                    "type", type.name(), "customerText", valueOrEmpty(context.customerText()),
                    "stage", valueOrEmpty(context.stage()), "recentFollowUps", valueOrEmpty(context.recentFollowUps())));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot hash AI input", exception);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
