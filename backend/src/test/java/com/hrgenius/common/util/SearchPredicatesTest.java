package com.hrgenius.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchPredicatesTest {

    @Test
    void wrapsLowercasedTermInWildcards() {
        assertThat(SearchPredicates.pattern("  Emma ")).isEqualTo("%emma%");
    }

    @Test
    void userWildcardsAreMatchedLiterally() {
        assertThat(SearchPredicates.pattern("50%_off")).isEqualTo("%50\\%\\_off%");
        assertThat(SearchPredicates.pattern("a\\b")).isEqualTo("%a\\\\b%");
    }
}
