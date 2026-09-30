package com.hrgenius.recruitment.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OfferFormattingTest {

    @Test
    void formatsRupeesWithIndianGrouping() {
        assertThat(OfferService.inr(new BigDecimal("999"))).isEqualTo("INR 999");
        assertThat(OfferService.inr(new BigDecimal("1000"))).isEqualTo("INR 1,000");
        assertThat(OfferService.inr(new BigDecimal("150000"))).isEqualTo("INR 1,50,000");
        assertThat(OfferService.inr(new BigDecimal("3000000"))).isEqualTo("INR 30,00,000");
        assertThat(OfferService.inr(new BigDecimal("12345678.60"))).isEqualTo("INR 1,23,45,679");
        assertThat(OfferService.inr(null)).isEqualTo("—");
    }

    @Test
    void resumeFileNamesAreReducedToASafeBasename() {
        assertThat(CandidateService.safeFileName("C:\\Users\\x\\My CV (final).pdf")).isEqualTo("My CV _final_.pdf");
        assertThat(CandidateService.safeFileName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(CandidateService.safeFileName(null)).isEqualTo("resume");
    }
}
