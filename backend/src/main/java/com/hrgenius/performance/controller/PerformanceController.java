package com.hrgenius.performance.controller;

import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.performance.dto.PerformanceDtos.*;
import com.hrgenius.performance.service.FeedbackService;
import com.hrgenius.performance.service.PerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Performance API. Cycle administration needs PERFORMANCE_ADMIN; review endpoints check per review
 * whether the caller is the employee, the reviewer, or an admin.
 */
@Tag(name = "Performance")
@RestController
@RequestMapping("/api/v1/performance")
public class PerformanceController {

    private static final String ADMIN = "hasAuthority('PERFORMANCE_ADMIN')";

    private final PerformanceService performance;
    private final FeedbackService feedback;
    private final CurrentUserService currentUser;

    public PerformanceController(PerformanceService performance, FeedbackService feedback, CurrentUserService currentUser) {
        this.performance = performance;
        this.feedback = feedback;
        this.currentUser = currentUser;
    }

    // ---- cycles (HR) ----

    @Operation(summary = "Review cycles")
    @GetMapping("/cycles")
    @PreAuthorize(ADMIN)
    public List<CycleDto> cycles() {
        return performance.cycles();
    }

    @Operation(summary = "Create a draft cycle")
    @PostMapping("/cycles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ADMIN)
    public CycleDto createCycle(@Valid @RequestBody CycleRequest req) {
        return performance.createCycle(req);
    }

    @Operation(summary = "Edit a cycle")
    @PutMapping("/cycles/{id}")
    @PreAuthorize(ADMIN)
    public CycleDto updateCycle(@PathVariable Long id, @Valid @RequestBody CycleRequest req) {
        return performance.updateCycle(id, req);
    }

    @Operation(summary = "Launch a cycle: opens reviews for eligible employees")
    @PostMapping("/cycles/{id}/launch")
    @PreAuthorize(ADMIN)
    public CycleDto launch(@PathVariable Long id) {
        return performance.launch(id);
    }

    @Operation(summary = "Close a cycle")
    @PostMapping("/cycles/{id}/close")
    @PreAuthorize(ADMIN)
    public CycleDto close(@PathVariable Long id) {
        return performance.close(id);
    }

    @Operation(summary = "Completion and rating distribution for a cycle")
    @GetMapping("/cycles/{id}/summary")
    @PreAuthorize(ADMIN)
    public CycleSummaryDto summary(@PathVariable Long id) {
        return performance.summary(id);
    }

    @Operation(summary = "All reviews in a cycle")
    @GetMapping("/cycles/{id}/reviews")
    @PreAuthorize(ADMIN)
    public List<ReviewSummaryDto> cycleReviews(@PathVariable Long id) {
        return performance.cycleReviews(id);
    }

    // ---- reviews ----

    @Operation(summary = "My reviews across cycles")
    @GetMapping("/me/reviews")
    public List<ReviewSummaryDto> myReviews() {
        return performance.mine(me());
    }

    @Operation(summary = "Reviews I am the reviewer for")
    @GetMapping("/team/reviews")
    public List<ReviewSummaryDto> teamReviews() {
        return performance.team(me());
    }

    @Operation(summary = "One review (shape depends on whether you are the employee, reviewer or HR)")
    @GetMapping("/reviews/{id}")
    public ReviewDto review(@PathVariable Long id) {
        return performance.review(id);
    }

    @Operation(summary = "Add a goal")
    @PostMapping("/reviews/{id}/goals")
    public ReviewDto addGoal(@PathVariable Long id, @Valid @RequestBody GoalRequest req) {
        return performance.addGoal(id, req);
    }

    @Operation(summary = "Edit a goal")
    @PutMapping("/reviews/{id}/goals/{goalId}")
    public ReviewDto updateGoal(@PathVariable Long id, @PathVariable Long goalId, @Valid @RequestBody GoalRequest req) {
        return performance.updateGoal(id, goalId, req);
    }

    @Operation(summary = "Remove a goal")
    @DeleteMapping("/reviews/{id}/goals/{goalId}")
    public ReviewDto deleteGoal(@PathVariable Long id, @PathVariable Long goalId) {
        return performance.deleteGoal(id, goalId);
    }

    @Operation(summary = "Update a goal's progress and status")
    @PatchMapping("/reviews/{id}/goals/{goalId}/progress")
    public ReviewDto progress(@PathVariable Long id, @PathVariable Long goalId, @Valid @RequestBody ProgressRequest req) {
        return performance.updateProgress(id, goalId, req);
    }

    @Operation(summary = "Save or submit my self-assessment")
    @PutMapping("/reviews/{id}/self")
    public ReviewDto self(@PathVariable Long id, @Valid @RequestBody AssessmentRequest req) {
        return performance.selfAssessment(id, req);
    }

    @Operation(summary = "Save or submit the manager assessment")
    @PutMapping("/reviews/{id}/manager")
    public ReviewDto manager(@PathVariable Long id, @Valid @RequestBody AssessmentRequest req) {
        return performance.managerAssessment(id, req);
    }

    @Operation(summary = "Acknowledge a completed review")
    @PostMapping("/reviews/{id}/acknowledge")
    public ReviewDto acknowledge(@PathVariable Long id, @Valid @RequestBody(required = false) AcknowledgeRequest req) {
        return performance.acknowledge(id, req);
    }

    // ---- feedback ----

    @Operation(summary = "Give praise or feedback")
    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackDto give(@Valid @RequestBody FeedbackRequest req) {
        return feedback.give(me(), req);
    }

    @Operation(summary = "Company kudos wall")
    @GetMapping("/feedback/wall")
    public List<FeedbackDto> wall(@RequestParam(defaultValue = "30") int limit) {
        return feedback.wall(limit);
    }

    @Operation(summary = "Feedback I received")
    @GetMapping("/feedback/received")
    public List<FeedbackDto> received() {
        return feedback.received(me());
    }

    @Operation(summary = "Feedback I gave")
    @GetMapping("/feedback/given")
    public List<FeedbackDto> given() {
        return feedback.given(me());
    }

    @Operation(summary = "Private feedback about my direct reports")
    @GetMapping("/feedback/team")
    public List<FeedbackDto> team() {
        return feedback.aboutMyTeam(me());
    }

    private Long me() {
        return currentUser.employeeId()
                .orElseThrow(() -> new BadRequestException("Your login is not linked to an employee profile"));
    }
}
