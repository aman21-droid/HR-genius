package com.hrgenius.employee.entity;

/** Enumerations used across the employee aggregate. Persisted as strings. */
public final class EmployeeEnums {

    private EmployeeEnums() {
    }

    public enum Gender { MALE, FEMALE, NON_BINARY, UNDISCLOSED }

    public enum MaritalStatus { SINGLE, MARRIED, DIVORCED, WIDOWED, UNDISCLOSED }

    public enum EmploymentType { FULL_TIME, PART_TIME, CONTRACT, INTERN }

    /** PROBATION and ACTIVE count toward headcount; ON_NOTICE still employed; EXITED is gone. */
    public enum EmployeeStatus { PROBATION, ACTIVE, ON_NOTICE, EXITED }

    public enum TaxRegime { OLD, NEW }

    public enum DocumentCategory {
        ID_PROOF, ADDRESS_PROOF, EDUCATION, EXPERIENCE, CONTRACT, VISA, CERTIFICATION, POLICY, OTHER
    }

    public enum TimelineEventType {
        JOINED, CONFIRMED, PROMOTED, DESIGNATION_CHANGED, TRANSFERRED, MANAGER_CHANGED,
        SALARY_REVISED, STATUS_CHANGED, EXITED, REHIRED
    }
}
