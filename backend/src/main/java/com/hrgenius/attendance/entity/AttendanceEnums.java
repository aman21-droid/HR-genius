package com.hrgenius.attendance.entity;

/** Enumerations for the attendance module. Persisted as strings. */
public final class AttendanceEnums {

    private AttendanceEnums() {
    }

    /** Resolved status of an employee's day. */
    public enum AttendanceStatus { PRESENT, ABSENT, ON_LEAVE, HOLIDAY, WEEKEND, HALF_DAY }

    /** Where the day's record came from. */
    public enum AttendanceSource { SELF, ADMIN, REGULARIZED, SYSTEM }

    /** Lifecycle of a regularization request. */
    public enum RegularizationStatus { PENDING, APPROVED, REJECTED, CANCELLED }
}
