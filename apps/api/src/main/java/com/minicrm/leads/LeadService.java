package com.minicrm.leads;

import com.minicrm.common.ActivityLogService;
import com.minicrm.common.ApiException;
import com.minicrm.common.BusinessRules;
import com.minicrm.common.PageSupport;
import com.minicrm.common.RoleCode;
import com.minicrm.common.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

@Service
public class LeadService {
  private final LeadRepository repository;
  private final LeadStateMachine stateMachine;
  private final ActivityLogService activityLogService;

  public LeadService(LeadRepository repository, LeadStateMachine stateMachine,
                     ActivityLogService activityLogService) {
    this.repository = repository;
    this.stateMachine = stateMachine;
    this.activityLogService = activityLogService;
  }

  public PageSupport.Result<Map<String, Object>> list(SecurityUser actor, Query query) {
    validateDate(query.from(), "from");
    validateDate(query.to(), "to");
    if (query.ownerId() != null && BusinessRules.isSales(actor) && !query.ownerId().equals(actor.id())) {
      throw forbidden("销售只能查看自己负责的线索");
    }
    if (query.ownerId() != null && !repository.userInOrganization(actor.organizationId(), query.ownerId())) {
      throw notFound();
    }
    return repository.findPage(actor, new LeadRepository.Query(
        query.page(), query.pageSize(), query.keyword(), parseStatus(query.status()), query.ownerId(),
        query.source(), query.from(), query.to(), query.archived(), query.sortBy(), query.sortOrder(),
        BusinessRules.isSales(actor)));
  }

  public Map<String, Object> get(SecurityUser actor, UUID id) {
    return get(actor, id, 1, 20);
  }

  public Map<String, Object> get(SecurityUser actor, UUID id, Integer timelinePage, Integer timelinePageSize) {
    Map<String, Object> lead = repository.findVisible(actor, id, timelinePage, timelinePageSize);
    if (lead == null) throw notFound();
    return lead;
  }

  @Transactional
  public Map<String, Object> create(SecurityUser actor, CreateRequest request, HttpServletRequest httpRequest) {
    requireWritable(actor);
    if (LeadContactNormalizer.blank(request.name()) == null
        && LeadContactNormalizer.blank(request.company()) == null) {
      throw validation("姓名或公司至少填写一个");
    }
    LeadStatus status = request.status() == null ? LeadStatus.NEW : parseStatus(request.status());
    if (status.terminal()) throw validation("创建线索不能直接进入终态");
    UUID ownerId = request.ownerId();
    if (BusinessRules.isSales(actor)) {
      if (ownerId != null && !ownerId.equals(actor.id())) throw forbidden("销售只能将线索分配给自己");
      ownerId = actor.id();
    } else if (ownerId != null && !repository.userInOrganization(actor.organizationId(), ownerId)) {
      throw notFound();
    }
    String email = LeadContactNormalizer.email(request.email());
    String phone = LeadContactNormalizer.phone(request.phone());
    rejectDuplicate(actor, email, phone, null);
    validateTags(actor.organizationId(), request.tagIds());
    UUID id;
    try {
      id = repository.insert(actor.organizationId(), request, email, phone, status, ownerId, actor.id());
    } catch (DataIntegrityViolationException exception) {
      throw duplicateOrRethrow(actor, email, phone, null, exception);
    }
    repository.replaceTags(id, request.tagIds());
    activityLogService.record(actor, "CREATE_LEAD", "LEAD", id, null, httpRequest);
    return get(actor, id);
  }

  @Transactional
  public Map<String, Object> update(SecurityUser actor, UUID id, UpdateRequest request,
                                    HttpServletRequest httpRequest) {
    requireWritable(actor);
    Map<String, Object> current = get(actor, id);
    BusinessRules.requireLeadOwnerOrAdmin(actor, (UUID) current.get("ownerId"));
    if (!BusinessRules.isAdmin(actor) && request.ownerId() != null) {
      throw forbidden("销售不能修改负责人");
    }
    UUID ownerId = request.ownerId();
    if (ownerId != null && !repository.userInOrganization(actor.organizationId(), ownerId)) throw notFound();
    String email = request.email() == null ? null : LeadContactNormalizer.email(request.email());
    String phone = request.phone() == null ? null : LeadContactNormalizer.phone(request.phone());
    rejectDuplicate(actor, email, phone, id);
    validateTags(actor.organizationId(), request.tagIds());
    try {
      repository.update(actor.organizationId(), id, request, email, phone, ownerId);
    } catch (DataIntegrityViolationException exception) {
      throw duplicateOrRethrow(actor, email, phone, id, exception);
    }
    if (request.tagIds() != null) repository.replaceTags(id, request.tagIds());
    activityLogService.record(actor, "UPDATE_LEAD", "LEAD", id, null, httpRequest);
    return get(actor, id);
  }

  @Transactional
  public Map<String, Object> archive(SecurityUser actor, UUID id, boolean restore,
                                     HttpServletRequest httpRequest) {
    requireWritable(actor);
    Map<String, Object> current = get(actor, id);
    BusinessRules.requireLeadOwnerOrAdmin(actor, (UUID) current.get("ownerId"));
    if (restore && !BusinessRules.isAdmin(actor)) throw forbidden("只有管理员可以恢复线索");
    repository.archive(actor.organizationId(), id, restore);
    activityLogService.record(actor, restore ? "RESTORE_LEAD" : "ARCHIVE_LEAD", "LEAD", id, null, httpRequest);
    return get(actor, id);
  }

  @Transactional
  public Map<String, Object> assign(SecurityUser actor, UUID id, UUID ownerId,
                                    HttpServletRequest httpRequest) {
    BusinessRules.requireAdmin(actor);
    get(actor, id);
    if (!repository.userInOrganization(actor.organizationId(), ownerId)) throw notFound();
    repository.assign(actor.organizationId(), id, ownerId);
    activityLogService.record(actor, "ASSIGN_LEAD", "LEAD", id, Map.of("ownerId", ownerId.toString()), httpRequest);
    return get(actor, id);
  }

  @Transactional
  public Map<String, Object> batchAssign(SecurityUser actor, BatchAssignRequest request,
                                         HttpServletRequest httpRequest) {
    BusinessRules.requireAdmin(actor);
    if (request.leadIds() == null || request.leadIds().isEmpty() || request.leadIds().size() > 100
        || request.leadIds().stream().distinct().count() != request.leadIds().size()) {
      throw validation("线索列表无效");
    }
    if (!repository.userInOrganization(actor.organizationId(), request.ownerId())) throw notFound();
    for (UUID leadId : request.leadIds()) get(actor, leadId);
    for (UUID leadId : request.leadIds()) repository.assign(actor.organizationId(), leadId, request.ownerId());
    activityLogService.record(actor, "BATCH_ASSIGN_LEAD", "LEAD", null,
        Map.of("ownerId", request.ownerId().toString(), "count", request.leadIds().size()), httpRequest);
    return Map.of("updated", request.leadIds().size(), "leadIds", request.leadIds());
  }

  @Transactional
  public Map<String, Object> changeStatus(SecurityUser actor, UUID id, ChangeStatusRequest request,
                                          HttpServletRequest httpRequest) {
    Map<String, Object> current = repository.findForUpdate(actor.organizationId(), id);
    if (current == null || (BusinessRules.isSales(actor)
        && !actor.id().equals(current.get("ownerId")))) throw notFound();
    LeadStatus from = (LeadStatus) current.get("status");
    LeadStatus requested = request.status() == null ? null : parseStatus(request.status());
    LeadStateMachine.Transition transition = stateMachine.evaluate(
        actor, from, requested, (UUID) current.get("ownerId"), current.get("archivedAt") != null);
    String requestedOutcomeNote = LeadContactNormalizer.blank(request.outcomeNote());
    String requestedLostReason = LeadContactNormalizer.blank(request.lostReason());
    if (transition.to().terminal() && requestedOutcomeNote == null) {
      throw new ApiException("OUTCOME_NOTE_REQUIRED", "进入终态必须填写结果说明", HttpStatus.BAD_REQUEST);
    }
    if (transition.to() == LeadStatus.LOST && requestedLostReason == null) {
      throw new ApiException("LOST_REASON_REQUIRED", "进入输单状态必须填写输单原因", HttpStatus.BAD_REQUEST);
    }
    String outcomeNote = transition.to().terminal() ? requestedOutcomeNote : null;
    String lostReason = transition.to() == LeadStatus.LOST ? requestedLostReason : null;
    repository.changeStatus(actor.organizationId(), id, transition, outcomeNote, lostReason);
    UUID historyId = repository.insertHistory(id, transition, actor.id(), request.note());
    activityLogService.record(actor, "CHANGE_LEAD_STATUS", "LEAD", id,
        Map.of("fromStatus", transition.from().value(), "toStatus", transition.to().value()), httpRequest);
    Map<String, Object> history = new java.util.LinkedHashMap<>();
    history.put("id", historyId);
    history.put("fromStatus", transition.from().value());
    history.put("toStatus", transition.to().value());
    history.put("note", LeadContactNormalizer.blank(request.note()));
    history.put("outcomeNote", outcomeNote);
    history.put("lostReason", lostReason);
    return Map.of("lead", get(actor, id), "history", history);
  }

  private void rejectDuplicate(SecurityUser actor, String email, String phone, UUID ignoredId) {
    List<UUID> matches = repository.duplicateIds(actor.organizationId(), email, phone, ignoredId);
    if (matches.isEmpty()) return;
    throw duplicateException(actor, matches);
  }

  private ApiException duplicateOrRethrow(SecurityUser actor, String email, String phone,
                                          UUID ignoredId, DataIntegrityViolationException exception) {
    String message = exception.getMostSpecificCause() == null
        ? "" : String.valueOf(exception.getMostSpecificCause().getMessage());
    if (exception instanceof DuplicateKeyException
        || message.contains("leads_org_normalized_email_unique")
        || message.contains("leads_org_normalized_phone_unique")) {
      // PostgreSQL aborts the current transaction after a unique violation, so a
      // follow-up candidate query would itself fail. The preflight path still
      // returns visible candidates for non-racing duplicates.
      return new ApiException("LEAD_DUPLICATE", "组织内已存在相同手机号或邮箱的线索",
          Map.of("candidateLeadIds", List.of()), HttpStatus.CONFLICT);
    }
    throw exception;
  }

  private ApiException duplicateException(SecurityUser actor, List<UUID> matches) {
    List<UUID> visible = new ArrayList<>();
    for (UUID match : matches) if (repository.findVisible(actor, match) != null) visible.add(match);
    return new ApiException("LEAD_DUPLICATE", "组织内已存在相同手机号或邮箱的线索",
        Map.of("candidateLeadIds", visible), HttpStatus.CONFLICT);
  }

  private void validateTags(UUID organizationId, List<UUID> tagIds) {
    if (tagIds == null) return;
    if (tagIds.size() > 20 || tagIds.stream().distinct().count() != tagIds.size()
        || !repository.tagsInOrganization(organizationId, tagIds)) throw validation("标签列表无效");
  }

  private void requireWritable(SecurityUser actor) {
    if (BusinessRules.has(actor, RoleCode.SUPPORT)) throw forbidden("支持成员只能读取线索");
  }

  private LeadStatus parseStatus(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LeadStatus.fromValue(value);
    } catch (IllegalArgumentException exception) {
      throw validation("线索状态无效");
    }
  }

  private void validateDate(String value, String field) {
    if (value == null || value.isBlank()) return;
    try {
      Instant.parse(value);
    } catch (RuntimeException exception) {
      throw validation(field + " 日期格式无效");
    }
  }

  private ApiException validation(String message) {
    return new ApiException("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST);
  }

  private ApiException forbidden(String message) {
    return new ApiException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
  }

  private ApiException notFound() {
    return new ApiException("RESOURCE_NOT_FOUND", "线索不存在", HttpStatus.NOT_FOUND);
  }

  public record Query(Integer page, Integer pageSize, String keyword, String status, UUID ownerId,
                      String source, String from, String to, Boolean archived, String sortBy,
                      String sortOrder) {}

  public record CreateRequest(
      @Size(max = 100) String name, @Size(max = 160) String company,
      @Size(max = 32) String phone, @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 60) String source, String industry, String region, String notes,
      String status, UUID ownerId, List<UUID> tagIds, java.time.Instant nextFollowUpAt) {}

  public record UpdateRequest(
      @Size(max = 100) String name, @Size(max = 160) String company,
      @Size(max = 32) String phone, @Email @Size(max = 254) String email,
      @Size(max = 60) String source, @Size(max = 80) String industry,
      @Size(max = 100) String region, String notes, UUID ownerId,
      List<UUID> tagIds, java.time.Instant nextFollowUpAt) {}

  public record ChangeStatusRequest(
      @Size(max = 20) String status, @Size(max = 500) String note,
      @Size(max = 500) String outcomeNote, @Size(max = 200) String lostReason) {}

  public record BatchAssignRequest(
      @NotNull @Size(min = 1, max = 100) List<UUID> leadIds,
      @NotNull UUID ownerId) {}
}
