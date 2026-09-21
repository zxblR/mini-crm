package com.minicrm.leads;

import com.minicrm.common.ApiException;
import com.minicrm.common.SecurityUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeadStateMachineTest {
  private final LeadStateMachine machine = new LeadStateMachine();
  private final UUID ownerId = UUID.randomUUID();

  @Test
  void salesCanOnlyAdvanceTheirOwnOpenLead() {
    var actor = user("SALES");

    var transition = machine.evaluate(actor, LeadStatus.NEW, LeadStatus.CONTACTED,
        actor.id(), false);

    assertThat(transition.from()).isEqualTo(LeadStatus.NEW);
    assertThat(transition.to()).isEqualTo(LeadStatus.CONTACTED);
  }

  @Test
  void salesCannotSkipOrCloseFromAnEarlierStatus() {
    var actor = user("SALES");

    assertTransitionRejected(() -> machine.evaluate(actor, LeadStatus.NEW,
        LeadStatus.QUALIFIED, ownerId, false));
    assertTransitionRejected(() -> machine.evaluate(actor, LeadStatus.CONTACTED,
        LeadStatus.WON, ownerId, false));
  }

  @Test
  void adminReopeningWithoutTargetDefaultsToNegotiation() {
    var transition = machine.evaluate(user("ADMIN"), LeadStatus.WON, null,
        ownerId, false);

    assertThat(transition.to()).isEqualTo(LeadStatus.NEGOTIATION);
  }

  @Test
  void adminCannotReopenToAnotherTerminalState() {
    assertTransitionRejected(() -> machine.evaluate(user("OWNER"), LeadStatus.WON,
        LeadStatus.LOST, ownerId, false));
  }

  @Test
  void supportAndArchivedLeadsCannotTransition() {
    assertThatThrownBy(() -> machine.evaluate(user("SUPPORT"), LeadStatus.NEW,
        LeadStatus.CONTACTED, ownerId, false))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("FORBIDDEN");
    assertTransitionRejected(() -> machine.evaluate(user("ADMIN"), LeadStatus.NEW,
        LeadStatus.CONTACTED, ownerId, true));
  }

  private SecurityUser user(String role) {
    return new SecurityUser(UUID.randomUUID(), UUID.randomUUID(), "Test", "test@example.com",
        null, List.of(role), List.of());
  }

  private void assertTransitionRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(ApiException.class)
        .satisfies(error -> {
          ApiException apiException = (ApiException) error;
          assertThat(apiException.code()).isEqualTo("STAGE_INVALID_TRANSITION");
          assertThat(apiException.status().value()).isEqualTo(409);
        });
  }
}
