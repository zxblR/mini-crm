package com.minicrm.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityLogEventsTest {
  @Test
  void followUpAndTaskActionsUseTheApprovedWireValues() {
    assertThat(ActivityLogEvents.CREATE_FOLLOW_UP).isEqualTo("CREATE_FOLLOW_UP");
    assertThat(ActivityLogEvents.UPDATE_FOLLOW_UP).isEqualTo("UPDATE_FOLLOW_UP");
    assertThat(ActivityLogEvents.DELETE_FOLLOW_UP).isEqualTo("DELETE_FOLLOW_UP");
    assertThat(ActivityLogEvents.CREATE_TASK).isEqualTo("CREATE_TASK");
    assertThat(ActivityLogEvents.UPDATE_TASK).isEqualTo("UPDATE_TASK");
    assertThat(ActivityLogEvents.COMPLETE_TASK).isEqualTo("COMPLETE_TASK");
    assertThat(ActivityLogEvents.CANCEL_TASK).isEqualTo("CANCEL_TASK");
    assertThat(ActivityLogEvents.SKIP_TASK_AUTO_CREATE).isEqualTo("SKIP_TASK_AUTO_CREATE");
  }
}
