package com.hrgenius.me;

import com.hrgenius.approval.service.ApprovalService;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.leave.entity.LeaveEnums.LeaveStatus;
import com.hrgenius.onboarding.entity.OnboardingEnums.PlanStatus;
import com.hrgenius.performance.service.PerformanceService;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationStage;
import com.hrgenius.recruitment.entity.RecruitmentEnums.InterviewStatus;
import com.hrgenius.recruitment.entity.RecruitmentEnums.OfferStatus;
import com.hrgenius.recruitment.entity.RecruitmentEnums.RequisitionStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Role-specific dashboard blocks. Each block is only computed for people entitled to it, so the
 * page never asks for data the viewer would be refused: hiring for recruitment managers and
 * approvers, team priorities for anyone with direct reports.
 */
@Service
public class DashboardService {

    public record StageCount(String stage, long count) {
    }

    public record Upcoming(String title, String detail, Instant at, String route) {
    }

    public record Joiner(String name, String role, LocalDate joiningDate) {
    }

    public record Hiring(long openRequisitions, long requisitionsAwaitingApproval, List<StageCount> pipeline,
                         long offersAwaitingApproval, long offersOut, List<Upcoming> interviews, List<Joiner> joiners,
                         long onboardingInProgress) {
    }

    public record Away(Long employeeId, String name, String leaveType, String color, LocalDate from, LocalDate to) {
    }

    public record PendingLeave(Long employeeId, String name, String leaveType, LocalDate from, LocalDate to, BigDecimal days) {
    }

    public record Team(long size, List<Away> awayToday, List<Away> awayThisWeek, List<PendingLeave> pendingLeave,
                       long reviewsToComplete) {
    }

    public record Dashboard(long approvalsPending, Hiring hiring, Team team) {
    }

    private final EntityManager em;
    private final CurrentUserService currentUser;
    private final ApprovalService approvals;
    private final PerformanceService performance;
    private final EmployeeRepository employees;

    public DashboardService(EntityManager em, CurrentUserService currentUser, ApprovalService approvals,
                            PerformanceService performance, EmployeeRepository employees) {
        this.em = em;
        this.currentUser = currentUser;
        this.approvals = approvals;
        this.performance = performance;
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public Dashboard forEmployee(Long me) {
        boolean recruits = currentUser.hasAuthority("RECRUITMENT_MANAGE") || currentUser.hasAuthority("RECRUITMENT_APPROVE");
        long reports = employees.countByManager_IdAndStatusNot(me, EmployeeStatus.EXITED);
        return new Dashboard(approvals.inboxCount(me), recruits ? hiring() : null, reports > 0 ? team(me, reports) : null);
    }

    private Hiring hiring() {
        Map<RequisitionStatus, Long> reqs = counts("select r.status, count(r) from JobRequisition r group by r.status",
                RequisitionStatus.class);
        Map<OfferStatus, Long> offers = counts("select o.status, count(o) from Offer o group by o.status", OfferStatus.class);

        List<StageCount> pipeline = em.createQuery("""
                        select a.stage, count(a) from JobApplication a
                        where a.requisition.status = :open and a.stage not in :terminal group by a.stage""", Object[].class)
                .setParameter("open", RequisitionStatus.OPEN)
                .setParameter("terminal", ApplicationStage.TERMINAL)
                .getResultList().stream()
                .sorted((x, y) -> ((ApplicationStage) x[0]).compareTo((ApplicationStage) y[0]))
                .map(r -> new StageCount(((ApplicationStage) r[0]).name(), (Long) r[1])).toList();

        Instant now = Instant.now();
        List<Upcoming> interviews = em.createQuery("""
                        select a.id, c.firstName, c.lastName, i.roundName, q.title, i.scheduledAt
                        from Interview i join i.application a join a.candidate c join a.requisition q
                        where i.status = :scheduled and i.scheduledAt between :from and :to order by i.scheduledAt""", Object[].class)
                .setParameter("scheduled", InterviewStatus.SCHEDULED)
                .setParameter("from", now)
                .setParameter("to", now.plus(7, ChronoUnit.DAYS))
                .setMaxResults(5)
                .getResultList().stream()
                .map(r -> new Upcoming(r[1] + " " + r[2], r[3] + " · " + r[4], (Instant) r[5], "/recruitment/applications/" + r[0]))
                .toList();

        List<Joiner> joiners = em.createQuery("""
                        select c.firstName, c.lastName, d.name, o.joiningDate
                        from Offer o join o.application a join a.candidate c left join o.designation d
                        where o.status = :accepted and o.joiningDate >= :today order by o.joiningDate""", Object[].class)
                .setParameter("accepted", OfferStatus.ACCEPTED)
                .setParameter("today", LocalDate.now())
                .setMaxResults(5)
                .getResultList().stream()
                .map(r -> new Joiner(r[0] + " " + r[1], (String) r[2], (LocalDate) r[3])).toList();

        long onboarding = em.createQuery("select count(p) from OnboardingPlan p where p.status = :s", Long.class)
                .setParameter("s", PlanStatus.IN_PROGRESS).getSingleResult();

        return new Hiring(reqs.getOrDefault(RequisitionStatus.OPEN, 0L), reqs.getOrDefault(RequisitionStatus.PENDING_APPROVAL, 0L),
                pipeline, offers.getOrDefault(OfferStatus.PENDING_APPROVAL, 0L),
                offers.getOrDefault(OfferStatus.SENT, 0L) + offers.getOrDefault(OfferStatus.APPROVED, 0L),
                interviews, joiners, onboarding);
    }

    private Team team(Long me, long size) {
        LocalDate today = LocalDate.now();
        LocalDate weekEnd = today.plusDays(6);
        List<Away> week = em.createQuery("""
                        select e.id, e.firstName, e.lastName, t.name, t.color, l.startDate, l.endDate
                        from LeaveRequest l join l.employee e join l.leaveType t
                        where e.manager.id = :me and l.status = :approved and l.startDate <= :to and l.endDate >= :from
                        order by l.startDate""", Object[].class)
                .setParameter("me", me)
                .setParameter("approved", LeaveStatus.APPROVED)
                .setParameter("from", today)
                .setParameter("to", weekEnd)
                .getResultList().stream()
                .map(r -> new Away((Long) r[0], r[1] + " " + r[2], (String) r[3], (String) r[4], (LocalDate) r[5], (LocalDate) r[6]))
                .toList();
        List<Away> todayAway = week.stream().filter(a -> !a.from().isAfter(today) && !a.to().isBefore(today)).toList();

        List<PendingLeave> pending = em.createQuery("""
                        select e.id, e.firstName, e.lastName, t.name, l.startDate, l.endDate, l.days
                        from LeaveRequest l join l.employee e join l.leaveType t
                        where e.manager.id = :me and l.status = :pending order by l.startDate""", Object[].class)
                .setParameter("me", me)
                .setParameter("pending", LeaveStatus.PENDING)
                .setMaxResults(5)
                .getResultList().stream()
                .map(r -> new PendingLeave((Long) r[0], r[1] + " " + r[2], (String) r[3], (LocalDate) r[4], (LocalDate) r[5], (BigDecimal) r[6]))
                .toList();

        long reviews = performance.team(me).stream()
                .filter(r -> "ACTIVE".equals(r.cycleStatus()) && "SELF_SUBMITTED".equals(r.status())).count();
        return new Team(size, todayAway, week, pending, reviews);
    }

    private <E extends Enum<E>> Map<E, Long> counts(String jpql, Class<E> type) {
        Map<E, Long> out = new EnumMap<>(type);
        em.createQuery(jpql, Object[].class).getResultList()
                .forEach(r -> out.put(type.cast(r[0]), (Long) r[1]));
        Arrays.stream(type.getEnumConstants()).forEach(e -> out.putIfAbsent(e, 0L));
        return out;
    }
}
