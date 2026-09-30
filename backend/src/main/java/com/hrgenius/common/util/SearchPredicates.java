package com.hrgenius.common.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * Case-insensitive "contains" search for Criteria queries.
 *
 * <p>Always uses an explicit escape character. Without one, Hibernate's H2 dialect renders
 * {@code LIKE ? ESCAPE ''}, and in H2's Oracle mode '' is NULL, so every LIKE silently matches
 * nothing. The user's term is escaped too, so '%' and '_' are matched literally rather than
 * acting as wildcards.
 */
public final class SearchPredicates {

    private static final char ESCAPE = '\\';

    private SearchPredicates() {
    }

    /** lower(expr) LIKE '%term%' ESCAPE '\' */
    public static Predicate containsIgnoreCase(CriteriaBuilder cb, Expression<String> expr, String term) {
        return cb.like(cb.lower(expr), pattern(term), ESCAPE);
    }

    static String pattern(String term) {
        String escaped = term.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
