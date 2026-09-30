package com.hrgenius.org.service;

import com.hrgenius.common.error.ResourceNotFoundException;

import java.util.Arrays;

/**
 * The org master types served by the generic /api/v1/org/{type} endpoints.
 * {@code employeeAttribute} is the Employee field referencing this master; it is a
 * compile-time constant, never user input, so it is safe to use when building JPQL.
 */
public enum MasterType {
    BUSINESS_UNIT("business-units", "Business unit", "businessUnit"),
    COST_CENTER("cost-centers", "Cost center", "costCenter"),
    DEPARTMENT("departments", "Department", "department"),
    DESIGNATION("designations", "Designation", "designation"),
    GRADE("grades", "Grade", "grade"),
    LOCATION("locations", "Location", "location");

    private final String slug;
    private final String label;
    private final String employeeAttribute;

    MasterType(String slug, String label, String employeeAttribute) {
        this.slug = slug;
        this.label = label;
        this.employeeAttribute = employeeAttribute;
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }

    public String employeeAttribute() {
        return employeeAttribute;
    }

    public static MasterType fromSlug(String slug) {
        return Arrays.stream(values())
                .filter(t -> t.slug.equals(slug))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Unknown org master type: " + slug));
    }
}
