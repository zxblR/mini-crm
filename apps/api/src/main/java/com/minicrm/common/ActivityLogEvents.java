package com.minicrm.common;

/** Centralized activity-log action and metadata values used by follow-up/task workflows. */
public final class ActivityLogEvents {
  public static final String CREATE_FOLLOW_UP = "CREATE_FOLLOW_UP";
  public static final String UPDATE_FOLLOW_UP = "UPDATE_FOLLOW_UP";
  public static final String DELETE_FOLLOW_UP = "DELETE_FOLLOW_UP";
  public static final String CREATE_TASK = "CREATE_TASK";
  public static final String UPDATE_TASK = "UPDATE_TASK";
  public static final String COMPLETE_TASK = "COMPLETE_TASK";
  public static final String CANCEL_TASK = "CANCEL_TASK";
  public static final String SKIP_TASK_AUTO_CREATE = "SKIP_TASK_AUTO_CREATE";

  public static final String OWNER_INACTIVE = "OWNER_INACTIVE";
  public static final String OWNER_NOT_FOUND = "OWNER_NOT_FOUND";

  private ActivityLogEvents() {}
}
