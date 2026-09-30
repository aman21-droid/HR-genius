package com.hrgenius.analytics.service;

import com.hrgenius.analytics.dto.AnalyticsDtos.*;
import com.hrgenius.compliance.entity.Policy;
import com.hrgenius.compliance.repository.PolicyAcknowledgementRepository;
import com.hrgenius.compliance.repository.PolicyRepository;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.helpdesk.entity.Ticket;
import com.hrgenius.helpdesk.repository.TicketRepository;
import com.hrgenius.leave.entity.LeaveEnums.LeaveStatus;
import com.hrgenius.leave.repository.LeaveRequestRepository;
import com.hrgenius.org.entity.MasterEntity;
import com.hrgenius.payroll.entity.PayrollRun;
import com.hrgenius.payroll.entity.PayrollRun.RunStatus;
import com.hrgenius.payroll.entity.Payslip;
import com.hrgenius.payroll.repository.PayrollRunRepository;
import com.hrgenius.payroll.repository.PayslipRepository;
import com.hrgenius.performance.entity.PerformanceEnums.ReviewStatus;
import com.hrgenius.performance.entity.PerformanceReview;
import com.hrgenius.performance.entity.ReviewCycle;
import com.hrgenius.performance.repository.PerformanceReviewRepository;
import com.hrgenius.performance.repository.ReviewCycleRepository;
import com.hrgenius.recruitment.entity.JobApplication;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationStage;
import com.hrgenius.recruitment.entity.RecruitmentEnums.RequisitionStatus;
import com.hrgenius.recruitment.repository.JobApplicationRepository;
import com.hrgenius.recruitment.repository.JobRequisitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Organisation-wide analytics computed on read from the operational tables. The data volumes of an
 * HRMS (hundreds to low thousands of employees) make in-memory aggregation straightforward; a
 * reporting store would be the next step beyond that.
 */
@Service
public class AnalyticsService {

    private final EmployeeRepository employees;
    private final LeaveRequestRepository leaveRequests;
    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final JobRequisitionRepository requisitions;
    private final JobApplicationRepository applications;
    private final ReviewCycleRepository cycles;
    private final PerformanceReviewRepository reviews;
    private final TicketRepository tickets;
    private final PolicyRepository policies;
    private final PolicyAcknowledgementRepository acks;

    public AnalyticsService(EmployeeRepository employees, LeaveRequestRepository leaveRequests, PayrollRunRepository runs,
                            PayslipRepository payslips, JobRequisitionRepository requisitions,
                            JobApplicationRepository applications, ReviewCycleRepository cycles,
                            PerformanceReviewRepository reviews, TicketRepository tickets, PolicyRepository policies,
                            PolicyAcknowledgementRepository acks) {
        this.employees = employees;
        this.leaveRequests = leaveRequests;
        this.runs = runs;
        this.payslips = payslips;
        this.requisitions = requisitions;
        this.applications = applications;
        this.cycles = cycles;
        this.reviews = reviews;
        this.tickets = tickets;
        this.policies = policies;
        this.acks = acks;
    }

    @Transactional(readOnly = true)
    public Overview overview(LocalDate today) {
        List<Employee> all = employees.findAll();
        List<Employee> current = all.stream().filter(e -> isCurrent(e, today)).toList();

        // ---- movement over the last 12 months
        List<MonthPoint> movement = new ArrayList<>();
        YearMonth thisMonth = YearMonth.from(today);
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = thisMonth.minusMonths(i);
            LocalDate end = i == 0 ? today : ym.atEndOfMonth();
            long joins = all.stream().filter(e -> YearMonth.from(e.getDateOfJoining()).equals(ym)).count();
            long exits = all.stream().filter(e -> e.getExitDate() != null && YearMonth.from(e.getExitDate()).equals(ym)).count();
            long hc = all.stream().filter(e -> isCurrent(e, end)).count();
            movement.add(new MonthPoint(ym.toString(), joins, exits, hc));
        }
        long joins12 = movement.stream().mapToLong(MonthPoint::joins).sum();
        long exits12 = movement.stream().mapToLong(MonthPoint::exits).sum();
        double avgHc = movement.stream().mapToLong(MonthPoint::headcount).average().orElse(0);
        BigDecimal attrition = avgHc == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(100.0 * exits12 / avgHc).setScale(1, RoundingMode.HALF_UP);

        // ---- tenure
        Map<String, Long> tenure = new LinkedHashMap<>();
        for (String b : List.of("< 1 yr", "1–3 yrs", "3–5 yrs", "5–10 yrs", "10+ yrs")) {
            tenure.put(b, 0L);
        }
        double tenureSum = 0;
        for (Employee e : current) {
            double years = ChronoUnit.DAYS.between(e.getDateOfJoining(), today) / 365.25;
            tenureSum += years;
            String bucket = years < 1 ? "< 1 yr" : years < 3 ? "1–3 yrs" : years < 5 ? "3–5 yrs" : years < 10 ? "5–10 yrs" : "10+ yrs";
            tenure.merge(bucket, 1L, Long::sum);
        }
        BigDecimal avgTenure = current.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(tenureSum / current.size()).setScale(1, RoundingMode.HALF_UP);

        // ---- leave taken this year (approved), by type
        LocalDate yearStart = today.withDayOfYear(1);
        Map<String, BigDecimal> leaveDays = new TreeMap<>();
        leaveRequests.findAll().stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED && !l.getStartDate().isBefore(yearStart))
                .forEach(l -> leaveDays.merge(l.getLeaveType().getName(), l.getDays(), BigDecimal::add));
        List<Slice> leave = leaveDays.entrySet().stream()
                .map(en -> new Slice(en.getKey(), en.getValue().setScale(0, RoundingMode.HALF_UP).longValue())).toList();

        // ---- payroll
        List<PayrollRun> runList = new ArrayList<>(runs.findAllByOrderByPeriodDesc());
        Collections.reverse(runList);
        List<PayrollPoint> trend = runList.stream()
                .filter(r -> r.getStatus() != RunStatus.DRAFT)
                .map(r -> new PayrollPoint(r.getPeriod(), r.getStatus().name(), r.getEmployeeCount(), r.getTotalGross(),
                        r.getTotalNet(), r.getTotalEmployerCost()))
                .toList();
        Optional<PayrollRun> latestPublished = runs.findAllByOrderByPeriodDesc().stream()
                .filter(PayrollRun::isPublished).findFirst();
        List<MoneySlice> costByDept = List.of();
        if (latestPublished.isPresent()) {
            Map<String, BigDecimal> byDept = new TreeMap<>();
            for (Payslip p : payslips.findByRun(latestPublished.get().getId())) {
                String dept = nameOr(p.getEmployee().getDepartment(), "Unassigned");
                byDept.merge(dept, p.getGrossEarnings().add(p.getEmployerPf()).add(p.getEmployerEsi()), BigDecimal::add);
            }
            costByDept = byDept.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .map(en -> new MoneySlice(en.getKey(), en.getValue())).toList();
        }

        // ---- recruitment
        List<JobApplication> apps = applications.findAll();
        Map<String, Long> funnel = new LinkedHashMap<>();
        for (ApplicationStage s : List.of(ApplicationStage.APPLIED, ApplicationStage.SCREENING, ApplicationStage.INTERVIEW,
                ApplicationStage.OFFER, ApplicationStage.HIRED)) {
            // A funnel counts everyone who reached at least this stage; rejected/withdrawn count at APPLIED only.
            int rank = s.ordinal();
            funnel.put(s.name(), apps.stream().filter(a -> !a.getStage().isTerminal() || a.getStage() == ApplicationStage.HIRED
                    ? a.getStage().ordinal() >= rank : rank == 0).count());
        }
        List<Slice> sources = count(apps, a -> a.getSource().name());
        OptionalDouble daysToHire = apps.stream().filter(a -> a.getStage() == ApplicationStage.HIRED)
                .mapToLong(a -> Duration.between(a.getCreatedAt(), a.getStageChangedAt()).toDays()).average();
        var reqs = requisitions.findAll();
        long openReqs = reqs.stream().filter(r -> r.getStatus() == RequisitionStatus.OPEN).count();
        long openPositions = reqs.stream().filter(r -> r.getStatus() == RequisitionStatus.OPEN).mapToLong(r -> r.getRemaining()).sum();

        // ---- performance: latest cycle with completed reviews
        List<Slice> ratings = List.of();
        String ratingCycle = null;
        for (ReviewCycle c : cycles.findAllByOrderByStartDateDesc()) {
            List<PerformanceReview> done = reviews.findByCycle(c.getId()).stream()
                    .filter(r -> r.getStatus() == ReviewStatus.MANAGER_SUBMITTED || r.getStatus() == ReviewStatus.ACKNOWLEDGED)
                    .toList();
            if (!done.isEmpty()) {
                ratingCycle = c.getName();
                List<Slice> dist = new ArrayList<>();
                for (int i = 1; i <= 5; i++) {
                    int n = i;
                    dist.add(new Slice(n + "★", done.stream().filter(r -> Objects.equals(r.getManagerRating(), n)).count()));
                }
                ratings = dist;
                break;
            }
        }

        // ---- helpdesk & policies
        List<Ticket> ticketList = tickets.findAll();
        long openTickets = ticketList.stream().filter(t -> t.getStatus() == Ticket.Status.OPEN || t.getStatus() == Ticket.Status.IN_PROGRESS).count();
        long overdueTickets = ticketList.stream().filter(Ticket::isOverdue).count();
        Set<Long> currentIds = current.stream().map(Employee::getId).collect(Collectors.toSet());
        List<Policy> ackPolicies = policies.findByStatusOrderByTitleAsc(Policy.Status.PUBLISHED).stream()
                .filter(Policy::isRequiresAck).toList();
        BigDecimal compliance = null;
        if (!ackPolicies.isEmpty() && !currentIds.isEmpty()) {
            long needed = (long) ackPolicies.size() * currentIds.size();
            long done = ackPolicies.stream().mapToLong(p -> acks.findByPolicyIdAndVersionNo(p.getId(), p.getVersionNo())
                    .stream().filter(a -> currentIds.contains(a.getEmployeeId())).count()).sum();
            compliance = BigDecimal.valueOf(100.0 * done / needed).setScale(1, RoundingMode.HALF_UP);
        }

        Kpis kpis = new Kpis(current.size(), joins12, exits12, attrition, avgTenure, openReqs, openPositions,
                daysToHire.isPresent() ? Math.round(daysToHire.getAsDouble() * 10) / 10.0 : null,
                openTickets, overdueTickets, compliance,
                latestPublished.map(PayrollRun::getTotalEmployerCost).orElse(null));

        return new Overview(kpis,
                count(current, e -> nameOr(e.getDepartment(), "Unassigned")),
                count(current, e -> nameOr(e.getLocation(), "Unassigned")),
                count(current, e -> label(e.getEmploymentType().name())),
                count(current, e -> e.getGender() != null ? label(e.getGender().name()) : "Not recorded"),
                tenure.entrySet().stream().map(en -> new Slice(en.getKey(), en.getValue())).toList(),
                movement, leave, trend, costByDept,
                latestPublished.map(PayrollRun::getPeriod).orElse(null),
                funnel.entrySet().stream().map(en -> new Slice(label(en.getKey()), en.getValue())).toList(),
                sources.stream().map(s -> new Slice(label(s.label()), s.value())).toList(),
                ratings, ratingCycle);
    }

    /** Current headcount as of a date: joined on or before it and not yet exited. */
    static boolean isCurrent(Employee e, LocalDate asOf) {
        if (e.getDateOfJoining().isAfter(asOf)) {
            return false;
        }
        if (e.getExitDate() != null) {
            return e.getExitDate().isAfter(asOf) || e.getExitDate().isEqual(asOf) && e.getStatus() != EmployeeStatus.EXITED;
        }
        return e.getStatus() != EmployeeStatus.EXITED;
    }

    private static <T> List<Slice> count(List<T> items, Function<T, String> key) {
        return items.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.counting()))
                .entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(en -> new Slice(en.getKey(), en.getValue())).toList();
    }

    private static String nameOr(MasterEntity m, String fallback) {
        return m == null ? fallback : m.getName();
    }

    private static String label(String enumName) {
        String s = enumName.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
