package com.hrgenius.performance.entity;

/** Enumerations for performance management. Persisted as strings. */
public final class PerformanceEnums {

    private PerformanceEnums() {
    }

    public enum CycleStatus { DRAFT, ACTIVE, CLOSED }

    public enum ReviewStatus { NOT_STARTED, SELF_SUBMITTED, MANAGER_SUBMITTED, ACKNOWLEDGED }

    public enum GoalStatus { NOT_STARTED, ON_TRACK, AT_RISK, OFF_TRACK, DONE }

    public enum FeedbackKind { PRAISE, CONSTRUCTIVE }

    public enum Visibility { PUBLIC, PRIVATE }
}
