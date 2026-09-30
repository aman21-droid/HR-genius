package com.hrgenius.analytics.controller;

import com.hrgenius.analytics.dto.AnalyticsDtos.Overview;
import com.hrgenius.analytics.service.AnalyticsExportService;
import com.hrgenius.analytics.service.AnalyticsService;
import com.hrgenius.common.web.Downloads;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Organisation analytics for ANALYTICS_VIEW holders (HR leadership and payroll). */
@Tag(name = "Analytics")
@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final AnalyticsExportService exports;

    public AnalyticsController(AnalyticsService analytics, AnalyticsExportService exports) {
        this.analytics = analytics;
        this.exports = exports;
    }

    @Operation(summary = "Workforce, payroll, recruitment, performance and service KPIs")
    @GetMapping("/overview")
    public Overview overview() {
        return analytics.overview(LocalDate.now());
    }

    @Operation(summary = "Headcount report (xlsx)")
    @GetMapping("/headcount.xlsx")
    public ResponseEntity<Resource> headcount() {
        LocalDate today = LocalDate.now();
        return Downloads.attachment(exports.headcount(today), "headcount-" + today + ".xlsx", Downloads.XLSX);
    }
}
