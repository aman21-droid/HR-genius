package com.hrgenius.onboarding.entity;

/** Enumerations for the onboarding module. Persisted as strings. */
public final class OnboardingEnums {

    private OnboardingEnums() {
    }

    /**
     * Who a checklist task belongs to. HR, MANAGER and EMPLOYEE resolve to a concrete person when
     * a plan starts; IT, ADMIN and FINANCE are team queues worked by anyone with ONBOARDING_MANAGE.
     */
    public enum OwnerRole { HR, MANAGER, EMPLOYEE, IT, ADMIN, FINANCE }

    public enum PlanStatus { IN_PROGRESS, COMPLETED }

    public enum TaskStatus { PENDING, DONE, SKIPPED }
}
