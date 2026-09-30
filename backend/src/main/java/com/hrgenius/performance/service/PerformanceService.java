package com.hrgenius.performance.service;

import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.performance.dto.PerformanceDtos.*;
import com.hrgenius.performance.entity.Goal;
import com.hrgenius.performance.entity.PerformanceEnums.CycleStatus;
import com.hrgenius.performance.entity.PerformanceEnums.ReviewStatus;
import com.hrgenius.performance.entity.PerformanceReview;
import com.hrgenius.performance.entity.ReviewCycle;
import com.hrgenius.performance.repository.GoalRepository;
import com.hrgenius.performance.repository.PerformanceReviewRepository;
import com.hrgenius.performance.repository.ReviewCycleRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Review cycles and the review workflow: goals -> self-assessment -> manager assessment ->
 * acknowledgement. The final score is the weight-averaged manager rating of the goals.
 */
@Service
public class PerformanceService {

    public static final String ADMIN_PERMISSION = "PERFORMANCE_ADMIN";
    private static final Set<ReviewStatus> COMPLETED = EnumSet.of(ReviewStatus.MANAGER_SUBMITTED, ReviewStatus.ACKNOWLEDGED);

    private enum Role { SELF, MANAGER, ADMIN }

    private final ReviewCycleRepository cycles;
    private final PerformanceReviewRepository reviews;
    private final GoalRepository goals;
    private final EmployeeRepository employees;
    private final CurrentUserService currentUser;

    public PerformanceService(ReviewCycleRepository cycles, PerformanceReviewRepository reviews, GoalRepository goals,
                              EmployeeRepository employees, CurrentUserService currentUser) {
        this.cycles = cycles;
        this.reviews = reviews;
        this.goals = goals;
        this.employees = employees;
        this.currentUser = currentUser;
    }

    // ================================================================ cycles (HR)

    @Transactional(readOnly = true)
    public List<CycleDto> cycles() {
        return cycles.findAllByOrderByStartDateDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public CycleDto createCycle(CycleRequest req) {
        ReviewCycle c = new ReviewCycle();
        applyCycle(c, req);
        cycles.save(c);
        return toDto(c);
    }

    @Transactional
    public CycleDto updateCycle(Long id, CycleRequest req) {
        ReviewCycle c = findCycle(id);
        if (c.getStatus() == CycleStatus.CLOSED) {
            throw new BusinessException("A closed cycle cannot be edited");
        }
        if (c.getStatus() == CycleStatus.ACTIVE
                && (!c.getStartDate().equals(req.startDate()) || !c.getEndDate().equals(req.endDate()))) {
            throw new BusinessException("The period of a launched cycle cannot change; only its name and due dates");
        }
        applyCycle(c, req);
        return toDto(c);
    }

    /** DRAFT -> ACTIVE: opens a review (reviewer = current manager) for each eligible employee. */
    @Transactional
    public CycleDto launch(Long id) {
        ReviewCycle c = findCycle(id);
        if (c.getStatus() != CycleStatus.DRAFT) {
            throw new BusinessException("Only a draft cycle can be launched");
        }
        int created = 0;
        for (Employee e : employees.findAll()) {
            boolean current = e.getStatus() != EmployeeStatus.EXITED
                    && (e.getExitDate() == null || e.getExitDate().isAfter(c.getEndDate()));
            if (!current || e.getManager() == null || e.getDateOfJoining().isAfter(c.getEndDate())
                    || reviews.existsByCycleIdAndEmployeeId(c.getId(), e.getId())) {
                continue;
            }
            PerformanceReview r = new PerformanceReview();
            r.setCycle(c);
            r.setEmployee(e);
            r.setReviewer(e.getManager());
            r.setStatus(ReviewStatus.NOT_STARTED);
            reviews.save(r);
            created++;
        }
        if (created == 0) {
            throw new BusinessException("No eligible employees for this cycle");
        }
        c.setStatus(CycleStatus.ACTIVE);
        c.setLaunchedAt(Instant.now());
        return toDto(c);
    }

    @Transactional
    public CycleDto close(Long id) {
        ReviewCycle c = findCycle(id);
        if (c.getStatus() != CycleStatus.ACTIVE) {
            throw new BusinessException("Only an active cycle can be closed");
        }
        c.setStatus(CycleStatus.CLOSED);
        c.setClosedAt(Instant.now());
        return toDto(c);
    }

    @Transactional(readOnly = true)
    public CycleSummaryDto summary(Long id) {
        ReviewCycle c = findCycle(id);
        List<PerformanceReview> list = reviews.findByCycle(id);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ReviewStatus s : ReviewStatus.values()) {
            byStatus.put(s.name(), list.stream().filter(r -> r.getStatus() == s).count());
        }
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            int rating = i;
            distribution.put(i, list.stream().filter(r -> COMPLETED.contains(r.getStatus())
                    && Objects.equals(r.getManagerRating(), rating)).count());
        }
        OptionalDouble avg = list.stream().filter(r -> r.getFinalScore() != null)
                .mapToDouble(r -> r.getFinalScore().doubleValue()).average();
        return new CycleSummaryDto(toDto(c), byStatus, distribution,
                avg.isPresent() ? BigDecimal.valueOf(avg.getAsDouble()).setScale(2, RoundingMode.HALF_UP) : null);
    }

    @Transactional(readOnly = true)
    public List<ReviewSummaryDto> cycleReviews(Long cycleId) {
        findCycle(cycleId);
        return reviews.findByCycle(cycleId).stream().map(this::toSummary).toList();
    }

    // ================================================================ review lists

    @Transactional(readOnly = true)
    public List<ReviewSummaryDto> mine(Long employeeId) {
        return reviews.findForEmployee(employeeId).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewSummaryDto> team(Long reviewerEmpId) {
        return reviews.findForReviewer(reviewerEmpId).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ReviewDto review(Long id) {
        PerformanceReview r = findReview(id);
        return toDto(r, roleOf(r));
    }

    // ================================================================ goals

    @Transactional
    public ReviewDto addGoal(Long reviewId, GoalRequest req) {
        PerformanceReview r = findReview(reviewId);
        Role role = roleOf(r);
        requireGoalEditing(r, role);
        Goal g = new Goal();
        applyGoal(g, req);
        g.setSortOrder(r.getGoals().size() * 10 + 10);
        r.addGoal(g);
        reviews.save(r);
        return toDto(r, role);
    }

    @Transactional
    public ReviewDto updateGoal(Long reviewId, Long goalId, GoalRequest req) {
        PerformanceReview r = findReview(reviewId);
        Role role = roleOf(r);
        requireGoalEditing(r, role);
        applyGoal(findGoal(r, goalId), req);
        return toDto(r, role);
    }

    @Transactional
    public ReviewDto deleteGoal(Long reviewId, Long goalId) {
        PerformanceReview r = findReview(reviewId);
        Role role = roleOf(r);
        requireGoalEditing(r, role);
        r.getGoals().remove(findGoal(r, goalId));
        return toDto(r, role);
    }

    @Transactional
    public ReviewDto updateProgress(Long reviewId, Long goalId, ProgressRequest req) {
        PerformanceReview r = findReview(reviewId);
        Role role = roleOf(r);
        if (!canUpdateProgress(r, role)) {
            throw new BusinessException("Progress can only be updated by the employee or their reviewer while the cycle is active");
        }
        Goal g = findGoal(r, goalId);
        g.setProgress(req.progress());
        g.setStatus(req.status());
        return toDto(r, role);
    }

    // ================================================================ assessments

    @Transactional
    public ReviewDto selfAssessment(Long reviewId, AssessmentRequest req) {
        PerformanceReview r = findReview(reviewId);
        Role role = roleOf(r);
        if (role != Role.SELF) {
            throw new AccessDeniedException("Only the employee can write their self-assessment");
        }
        requireActive(r);
        if (r.getStatus() != ReviewStatus.NOT_STARTED) {
            throw new BusinessException("Your self-assessment has already been submitted");
        }
        applyRatings(r, req, true);
        r.setSelfRating(req.overallRating());
        r.setSelfComments(blankToNull(req.comments()));
        if (Boolean.TRUE.equals(req.submit())) {
            validateComplete(r, true);
            r.setStatus(ReviewStatus.SELF_SUBMITTED);
            r.setSelfSubmittedAt(Instant.now());
        }
        return toDto(r, role);
    }

    @Transactional
    public ReviewDto managerAssessment(Long reviewId, AssessmentRequest req) {
        PerformanceReview r = findReview(reviewId);
        Role role = roleOf(r);
        if (role != Role.MANAGER) {
            throw new AccessDeniedException("Only the assigned reviewer can write the manager assessment");
        }
        requireActive(r);
        if (r.getStatus() != ReviewStatus.SELF_SUBMITTED) {
            throw new BusinessException(r.getStatus() == ReviewStatus.NOT_STARTED
                    ? "Wait for the employee to submit their self-assessment"
                    : "The manager assessment has already been submitted");
        }
        applyRatings(r, req, false);
        r.setManagerRating(req.overallRating());
        r.setManagerComments(blankToNull(req.comments()));
        if (Boolean.TRUE.equals(req.submit())) {
            validateComplete(r, false);
            r.setFinalScore(finalScore(r));
            r.setStatus(ReviewStatus.MANAGER_SUBMITTED);
            r.setManagerSubmittedAt(Instant.now());
        }
        return toDto(r, role);
    }

    @Transactional
    public ReviewDto acknowledge(Long reviewId, AcknowledgeRequest req) {
        PerformanceReview r = findReview(reviewId);
        Role role = roleOf(r);
        if (role != Role.SELF) {
            throw new AccessDeniedException("Only the employee can acknowledge their review");
        }
        if (r.getStatus() != ReviewStatus.MANAGER_SUBMITTED) {
            throw new BusinessException("There is no completed review to acknowledge yet");
        }
        r.setStatus(ReviewStatus.ACKNOWLEDGED);
        r.setAcknowledgedAt(Instant.now());
        r.setAckComment(req == null ? null : blankToNull(req.comment()));
        return toDto(r, role);
    }

    /** Weighted average of manager goal ratings (weights total 100), two decimals. */
    static BigDecimal finalScore(PerformanceReview r) {
        BigDecimal sum = BigDecimal.ZERO;
        int weights = 0;
        for (Goal g : r.getGoals()) {
            if (g.getManagerRating() != null) {
                sum = sum.add(BigDecimal.valueOf((long) g.getWeight() * g.getManagerRating()));
                weights += g.getWeight();
            }
        }
        return weights == 0 ? null : sum.divide(BigDecimal.valueOf(weights), 2, RoundingMode.HALF_UP);
    }

    // ================================================================ rules

    private Role roleOf(PerformanceReview r) {
        Long me = currentUser.employeeId().orElse(null);
        if (me != null && me.equals(r.getEmployee().getId())) {
            return Role.SELF;
        }
        if (me != null && me.equals(r.getReviewer().getId())) {
            return Role.MANAGER;
        }
        if (currentUser.hasAuthority(ADMIN_PERMISSION)) {
            return Role.ADMIN;
        }
        throw new AccessDeniedException("Not allowed to view this review");
    }

    private boolean canEditGoals(PerformanceReview r, Role role) {
        return role != Role.ADMIN && r.getCycle().getStatus() == CycleStatus.ACTIVE && r.getStatus() == ReviewStatus.NOT_STARTED;
    }

    private boolean canUpdateProgress(PerformanceReview r, Role role) {
        return role != Role.ADMIN && r.getCycle().getStatus() == CycleStatus.ACTIVE && r.getStatus() != ReviewStatus.ACKNOWLEDGED;
    }

    private void requireGoalEditing(PerformanceReview r, Role role) {
        if (!canEditGoals(r, role)) {
            throw new BusinessException("Goals can only change while the cycle is active and before the self-assessment is submitted");
        }
    }

    private void requireActive(PerformanceReview r) {
        if (r.getCycle().getStatus() != CycleStatus.ACTIVE) {
            throw new BusinessException("The " + r.getCycle().getName() + " cycle is " + r.getCycle().getStatus().name().toLowerCase());
        }
    }

    private void applyRatings(PerformanceReview r, AssessmentRequest req, boolean self) {
        Map<Long, Goal> byId = r.getGoals().stream().collect(Collectors.toMap(Goal::getId, g -> g));
        for (GoalRatingInput in : req.goals()) {
            Goal g = byId.get(in.goalId());
            if (g == null) {
                throw new BadRequestException("Goal " + in.goalId() + " is not part of this review");
            }
            if (self) {
                g.setSelfRating(in.rating());
                g.setSelfComment(blankToNull(in.comment()));
            } else {
                g.setManagerRating(in.rating());
                g.setManagerComment(blankToNull(in.comment()));
            }
        }
    }

    private void validateComplete(PerformanceReview r, boolean self) {
        if (r.getGoals().isEmpty()) {
            throw new BusinessException("Add at least one goal before submitting");
        }
        int total = r.getGoals().stream().mapToInt(Goal::getWeight).sum();
        if (total != 100) {
            throw new BusinessException("Goal weights must add up to 100% (currently " + total + "%)");
        }
        boolean allRated = r.getGoals().stream().allMatch(g -> (self ? g.getSelfRating() : g.getManagerRating()) != null);
        if (!allRated) {
            throw new BusinessException("Rate every goal before submitting");
        }
        if ((self ? r.getSelfRating() : r.getManagerRating()) == null) {
            throw new BusinessException("Give an overall rating before submitting");
        }
    }

    // ================================================================ mapping

    private ReviewDto toDto(PerformanceReview r, Role role) {
        boolean showSelf = role == Role.SELF || r.getStatus() != ReviewStatus.NOT_STARTED;
        boolean showManager = role == Role.MANAGER || COMPLETED.contains(r.getStatus());
        ReviewCycle c = r.getCycle();
        Employee e = r.getEmployee();
        List<GoalDto> goalDtos = r.getGoals().stream().map(g -> new GoalDto(g.getId(), g.getTitle(), g.getDescription(),
                g.getWeight(), g.getTargetDate(), g.getProgress(), g.getStatus().name(),
                showSelf ? g.getSelfRating() : null, showSelf ? g.getSelfComment() : null,
                showManager ? g.getManagerRating() : null, showManager ? g.getManagerComment() : null)).toList();
        int totalWeight = r.getGoals().stream().mapToInt(Goal::getWeight).sum();
        boolean active = c.getStatus() == CycleStatus.ACTIVE;
        return new ReviewDto(r.getId(), c.getId(), c.getName(), c.getStatus().name(), c.getSelfReviewDue(),
                c.getManagerReviewDue(), e.getId(), e.getEmployeeCode(), e.getFullName(),
                e.getDesignation() != null ? e.getDesignation().getName() : null,
                r.getReviewer().getId(), r.getReviewer().getFullName(), r.getStatus().name(),
                showSelf ? r.getSelfRating() : null, showSelf ? r.getSelfComments() : null,
                showManager ? r.getManagerRating() : null, showManager ? r.getManagerComments() : null,
                showManager ? r.getFinalScore() : null, r.getAckComment(), r.getSelfSubmittedAt(),
                r.getManagerSubmittedAt(), r.getAcknowledgedAt(), goalDtos, totalWeight, role.name(),
                canEditGoals(r, role), canUpdateProgress(r, role),
                role == Role.SELF && active && r.getStatus() == ReviewStatus.NOT_STARTED,
                role == Role.MANAGER && active && r.getStatus() == ReviewStatus.SELF_SUBMITTED,
                role == Role.SELF && r.getStatus() == ReviewStatus.MANAGER_SUBMITTED);
    }

    private ReviewSummaryDto toSummary(PerformanceReview r) {
        Employee e = r.getEmployee();
        boolean done = COMPLETED.contains(r.getStatus());
        return new ReviewSummaryDto(r.getId(), r.getCycle().getId(), r.getCycle().getName(), r.getCycle().getStatus().name(),
                e.getId(), e.getEmployeeCode(), e.getFullName(), e.getDesignation() != null ? e.getDesignation().getName() : null,
                r.getReviewer().getId(), r.getReviewer().getFullName(), r.getStatus().name(), r.getGoals().size(),
                done ? r.getManagerRating() : null, done ? r.getFinalScore() : null,
                r.getSelfSubmittedAt(), r.getManagerSubmittedAt());
    }

    private CycleDto toDto(ReviewCycle c) {
        List<PerformanceReview> list = c.getId() == null ? List.of() : reviews.findByCycle(c.getId());
        int completed = (int) list.stream().filter(r -> COMPLETED.contains(r.getStatus())).count();
        return new CycleDto(c.getId(), c.getName(), c.getStartDate(), c.getEndDate(), c.getSelfReviewDue(),
                c.getManagerReviewDue(), c.getStatus().name(), c.getLaunchedAt(), c.getClosedAt(), list.size(), completed);
    }

    private void applyCycle(ReviewCycle c, CycleRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new BadRequestException("The cycle cannot end before it starts");
        }
        if (req.selfReviewDue() != null && req.managerReviewDue() != null && req.managerReviewDue().isBefore(req.selfReviewDue())) {
            throw new BadRequestException("Manager reviews cannot be due before self-reviews");
        }
        c.setName(req.name().trim());
        c.setStartDate(req.startDate());
        c.setEndDate(req.endDate());
        c.setSelfReviewDue(req.selfReviewDue());
        c.setManagerReviewDue(req.managerReviewDue());
    }

    private static void applyGoal(Goal g, GoalRequest req) {
        g.setTitle(req.title().trim());
        g.setDescription(blankToNull(req.description()));
        g.setWeight(req.weight());
        g.setTargetDate(req.targetDate());
    }

    private ReviewCycle findCycle(Long id) {
        return cycles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Review cycle " + id + " not found"));
    }

    private PerformanceReview findReview(Long id) {
        return reviews.findById(id).orElseThrow(() -> new ResourceNotFoundException("Review " + id + " not found"));
    }

    private static Goal findGoal(PerformanceReview r, Long goalId) {
        return r.getGoals().stream().filter(g -> g.getId().equals(goalId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Goal " + goalId + " not found in this review"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
