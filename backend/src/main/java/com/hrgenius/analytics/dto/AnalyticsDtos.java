package com.hrgenius.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

/** Read-only analytics shapes. Series are ordered for display. */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record Slice(String label, long value) {
    }

    public record MonthPoint(String month, long joins, long exits, long headcount) {
    }

    public record PayrollPoint(String period, String status, int employees, BigDecimal gross, BigDecimal net,
                               BigDecimal employerCost) {
    }

    public record MoneySlice(String label, BigDecimal value) {
    }

    public record Kpis(long headcount, long joinsLast12m, long exitsLast12m, BigDecimal attritionRate,
                       BigDecimal averageTenureYears, long openRequisitions, long openPositions,
                       Double averageDaysToHire, long openTickets, long overdueTickets,
                       BigDecimal policyCompliance, BigDecimal latestMonthlyPayroll) {
    }

    public record Overview(Kpis kpis,
                           List<Slice> byDepartment, List<Slice> byLocation, List<Slice> byEmploymentType,
                           List<Slice> byGender, List<Slice> tenure, List<MonthPoint> movement,
                           List<Slice> leaveDaysByType, List<PayrollPoint> payrollTrend,
                           List<MoneySlice> payrollByDepartment, String payrollByDepartmentPeriod,
                           List<Slice> recruitmentFunnel, List<Slice> candidateSources,
                           List<Slice> ratingDistribution, String ratingCycle) {
    }
}
