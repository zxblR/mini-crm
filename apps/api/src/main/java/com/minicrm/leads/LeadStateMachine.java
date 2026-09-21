package com.minicrm.leads;

import com.minicrm.common.ApiException;
import com.minicrm.common.BusinessRules;
import com.minicrm.common.SecurityUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class LeadStateMachine {
  private static final Map<LeadStatus, LeadStatus> NEXT_OPEN = Map.of(
      LeadStatus.NEW, LeadStatus.CONTACTED,
      LeadStatus.CONTACTED, LeadStatus.QUALIFIED,
      LeadStatus.QUALIFIED, LeadStatus.PROPOSAL,
      LeadStatus.PROPOSAL, LeadStatus.NEGOTIATION);
  private static final Set<LeadStatus> OPEN = EnumSet.of(
      LeadStatus.NEW, LeadStatus.CONTACTED, LeadStatus.QUALIFIED,
      LeadStatus.PROPOSAL, LeadStatus.NEGOTIATION);

  public Transition evaluate(
      SecurityUser actor,
      LeadStatus current,
      LeadStatus requested,
      java.util.UUID ownerId,
      boolean archived) {
    if (!BusinessRules.isAdmin(actor)
        && BusinessRules.has(actor, com.minicrm.common.RoleCode.SUPPORT)) {
      throw forbidden();
    }
    if (archived) {
      throw invalid("归档线索不可迁移");
    }

    LeadStatus target = requested;
    if (target == null) {
      if (current.terminal() && BusinessRules.isAdmin(actor)) {
        target = LeadStatus.NEGOTIATION;
      } else {
        throw invalid("必须指定目标状态");
      }
    }
    if (current == target) {
      throw invalid("不能重复进入当前状态");
    }

    if (BusinessRules.isSales(actor)) {
      if (ownerId == null || !ownerId.equals(actor.id())) {
        throw invalid("销售只能迁移自己负责的线索");
      }
      if (current.terminal() || !salesAllowed(current, target)) {
        throw invalid("销售只能按标准路径推进线索");
      }
    } else if (BusinessRules.isAdmin(actor)) {
      if (current.terminal() && target.terminal()) {
        throw invalid("终态重开必须指定非终态目标");
      }
    } else {
      throw forbidden();
    }
    return new Transition(current, target);
  }

  public boolean isOpen(LeadStatus status) {
    return OPEN.contains(status);
  }

  private boolean salesAllowed(LeadStatus current, LeadStatus target) {
    LeadStatus next = NEXT_OPEN.get(current);
    return target == next
        || (current == LeadStatus.NEGOTIATION
            && (target == LeadStatus.WON || target == LeadStatus.LOST));
  }

  private ApiException invalid(String message) {
    return new ApiException("STAGE_INVALID_TRANSITION", message, HttpStatus.CONFLICT);
  }

  private ApiException forbidden() {
    return new ApiException("FORBIDDEN", "当前用户没有执行此操作的权限", HttpStatus.FORBIDDEN);
  }

  public record Transition(LeadStatus from, LeadStatus to) {}
}
