package com.hrgenius.leave.entity;

/** Enumerations for the leave module. Persisted as strings. */
public final class LeaveEnums {

    private LeaveEnums() {
    }

    /** How a leave type grants balance. NONE = manual only (e.g. comp-off, loss of pay). */
    public enum AccrualMethod { NONE, MONTHLY, ANNUAL }

    /** Lifecycle of a leave request. */
    public enum LeaveStatus { PENDING, APPROVED, REJECTED, CANCELLED }
}
