package com.hrgenius.me;

import com.hrgenius.approval.service.ApprovalService;
import com.hrgenius.attendance.service.HolidayService;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.compliance.service.PolicyService;
import com.hrgenius.helpdesk.service.HelpdeskService;
import com.hrgenius.leave.service.LeaveService;
import com.hrgenius.onboarding.service.OnboardingService;
import com.hrgenius.payroll.service.PayslipService;
import com.hrgenius.performance.service.FeedbackService;
import com.hrgenius.performance.service.PerformanceService;
import com.hrgenius.recruitment.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One call for the home dashboard and the to-do bell: everything waiting on the signed-in employee
 * across modules, plus a few personal highlights. Each module's own access rules still apply because
 * the data comes from the same services the module screens use.
 */
@Tag(name = "Me")
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    /** Something the user should act on; {@code route} is the SPA path that resolves it. */
    public record Todo(String kind, String title, String detail, String route, Instant due) {
    }

    public record Balance(String code, String name, String color, BigDecimal available) {
    }

    public record Payslip(Long id, String period, BigDecimal net) {
    }

    public record Holiday(LocalDate date, String name, boolean optional) {
    }

    public record Kudos(String fromName, String toName, String message, Instant createdAt) {
    }

    public record Summary(List<Todo> todos, List<Balance> leaveBalances, Payslip latestPayslip, List<Holiday> holidays,
                          List<Kudos> kudos, String reviewStatus, Long reviewId, long openTickets) {
    }

    private final CurrentUserService currentUser;
    private final ApprovalService approvals;
    private final LeaveService leave;
    private final OnboardingService onboarding;
    private final InterviewService interviews;
    private final PolicyService policies;
    private final HelpdeskService helpdesk;
    private final PayslipService payslips;
    private final PerformanceService performance;
    private final FeedbackService feedback;
    private final HolidayService holidays;

    public MeController(CurrentUserService currentUser, ApprovalService approvals, LeaveService leave,
                        OnboardingService onboarding, InterviewService interviews, PolicyService policies,
                        HelpdeskService helpdesk, PayslipService payslips, PerformanceService performance,
                        FeedbackService feedback, HolidayService holidays) {
        this.currentUser = currentUser;
        this.approvals = approvals;
        this.leave = leave;
        this.onboarding = onboarding;
        this.interviews = interviews;
        this.policies = policies;
        this.helpdesk = helpdesk;
        this.payslips = payslips;
        this.performance = performance;
        this.feedback = feedback;
        this.holidays = holidays;
    }

    @Operation(summary = "My to-dos and personal highlights for the dashboard")
    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public Summary summary() {
        Long me = currentUser.employeeId()
                .orElseThrow(() -> new BadRequestException("Your login is not linked to an employee profile"));
        List<Todo> todos = new ArrayList<>();

        long inbox = approvals.inboxCount(me);
        if (inbox > 0) {
            todos.add(new Todo("APPROVAL", inbox + " request" + (inbox == 1 ? "" : "s") + " awaiting your approval",
                    "Leave, requisitions, offers or payroll", "/approvals", null));
        }
        interviews.mine(me).stream()
                .filter(i -> !i.submitted() && "SCHEDULED".equals(i.interviewStatus()) && i.scheduledAt().isBefore(Instant.now()))
                .forEach(i -> todos.add(new Todo("FEEDBACK", "Scorecard owed: " + i.candidateName(),
                        i.roundName() + " for " + i.requisitionTitle(), "/interviews", i.scheduledAt())));
        interviews.mine(me).stream()
                .filter(i -> !i.submitted() && "SCHEDULED".equals(i.interviewStatus()) && i.scheduledAt().isAfter(Instant.now()))
                .limit(3)
                .forEach(i -> todos.add(new Todo("INTERVIEW", "Interview: " + i.candidateName(),
                        i.roundName() + " for " + i.requisitionTitle(), "/interviews", i.scheduledAt())));
        policies.pendingFor(me).forEach(p -> todos.add(new Todo("POLICY", "Acknowledge: " + p.title(),
                "Version " + p.versionNo(), "/policies", null)));
        onboarding.myTasks(me).stream().limit(5).forEach(t -> todos.add(new Todo("ONBOARDING", t.title(),
                "Onboarding · " + t.newHireName(), "/onboarding",
                t.dueDate() == null ? null : t.dueDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())));

        String reviewStatus = null;
        Long reviewId = null;
        var myReviews = performance.mine(me).stream().filter(r -> "ACTIVE".equals(r.cycleStatus())).toList();
        if (!myReviews.isEmpty()) {
            var r = myReviews.get(0);
            reviewStatus = r.status();
            reviewId = r.id();
            if ("NOT_STARTED".equals(r.status())) {
                todos.add(new Todo("REVIEW", "Complete your self-assessment", r.cycleName(), "/performance/reviews/" + r.id(), null));
            } else if ("MANAGER_SUBMITTED".equals(r.status())) {
                todos.add(new Todo("REVIEW", "Acknowledge your review", r.cycleName(), "/performance/reviews/" + r.id(), null));
            }
        }
        long toReview = performance.team(me).stream()
                .filter(r -> "ACTIVE".equals(r.cycleStatus()) && "SELF_SUBMITTED".equals(r.status())).count();
        if (toReview > 0) {
            todos.add(new Todo("REVIEW", toReview + " team review" + (toReview == 1 ? "" : "s") + " to complete",
                    "Self-assessments are in", "/performance", null));
        }
        var tickets = helpdesk.mine(me);
        tickets.stream().filter(t -> "RESOLVED".equals(t.status())).forEach(t -> todos.add(new Todo("TICKET",
                "Confirm fix: " + t.subject(), t.ticketNo() + " was resolved", "/helpdesk/tickets/" + t.id(), null)));
        long openTickets = tickets.stream().filter(t -> "OPEN".equals(t.status()) || "IN_PROGRESS".equals(t.status())).count();

        List<Balance> balances = leave.balances(me, LocalDate.now().getYear()).stream()
                .filter(b -> b.available().signum() > 0 || b.used().signum() > 0)
                .map(b -> new Balance(b.code(), b.name(), b.color(), b.available())).toList();
        Payslip latest = payslips.myPayslipPeriods(me).stream().findFirst()
                .map(p -> new Payslip(p.id(), p.period(), p.totalNet())).orElse(null);
        LocalDate today = LocalDate.now();
        List<Holiday> upcoming = holidays.listByYear(today.getYear()).stream()
                .filter(h -> !h.holidayDate().isBefore(today)).limit(3)
                .map(h -> new Holiday(h.holidayDate(), h.name(), h.optionalHoliday())).toList();
        if (upcoming.size() < 3) {
            List<Holiday> more = new ArrayList<>(upcoming);
            holidays.listByYear(today.getYear() + 1).stream().limit(3 - upcoming.size())
                    .forEach(h -> more.add(new Holiday(h.holidayDate(), h.name(), h.optionalHoliday())));
            upcoming = more;
        }
        List<Kudos> kudos = feedback.wall(3).stream()
                .map(f -> new Kudos(f.fromName(), f.toName(), f.message(), f.createdAt())).toList();

        return new Summary(todos, balances, latest, upcoming, kudos, reviewStatus, reviewId, openTickets);
    }
}
