package com.hrgenius.approval.service;

import com.hrgenius.approval.entity.ApprovalEnums.ApprovalStatus;
import com.hrgenius.approval.entity.ApprovalEnums.StepStatus;
import com.hrgenius.approval.entity.ApprovalRequest;
import com.hrgenius.approval.entity.ApprovalStep;
import com.hrgenius.approval.repository.ApprovalRequestRepository;
import com.hrgenius.approval.repository.ApprovalStepRepository;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generic multi-step approval engine. Domains submit a flow with an ordered approver chain and
 * plug in an {@link ApprovalOutcomeHandler} for their subject type; the engine advances steps and
 * calls the handler back once when the flow resolves. Handlers are resolved lazily through an
 * {@link ObjectProvider} because a handler (e.g. LeaveService) also depends on this service.
 */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository requests;
    private final ApprovalStepRepository steps;
    private final ObjectProvider<List<ApprovalOutcomeHandler>> handlersProvider;

    public ApprovalService(ApprovalRequestRepository requests, ApprovalStepRepository steps,
                           ObjectProvider<List<ApprovalOutcomeHandler>> handlersProvider) {
        this.requests = requests;
        this.steps = steps;
        this.handlersProvider = handlersProvider;
    }

    /**
     * Open an approval flow. An empty chain resolves immediately as APPROVED (nobody to ask),
     * and the handler's onApproved runs before returning.
     */
    @Transactional
    public ApprovalRequest submit(String subjectType, Long subjectId, Long requesterEmpId,
                                  String title, List<Approver> chain) {
        ApprovalRequest req = new ApprovalRequest();
        req.setSubjectType(subjectType);
        req.setSubjectId(subjectId);
        req.setRequesterEmpId(requesterEmpId);
        req.setTitle(title);

        if (chain == null || chain.isEmpty()) {
            req.setStatus(ApprovalStatus.APPROVED);
            req.setCurrentStep(0);
            req.setTotalSteps(0);
            req.setResolvedAt(Instant.now());
            ApprovalRequest saved = requests.save(req);
            handlerFor(subjectType).onApproved(saved);
            return saved;
        }

        req.setStatus(ApprovalStatus.PENDING);
        req.setCurrentStep(1);
        req.setTotalSteps(chain.size());
        int no = 1;
        for (Approver a : chain) {
            ApprovalStep step = new ApprovalStep();
            step.setStepNo(no++);
            step.setApproverEmpId(a.employeeId());
            step.setRoleHint(a.roleHint());
            step.setStatus(StepStatus.PENDING);
            req.addStep(step);
        }
        return requests.save(req);
    }

    /** Approve or reject the active step of a flow. Only the current step's approver may act. */
    @Transactional
    public ApprovalRequest decide(Long stepId, Long actingEmpId, boolean approve, String comment) {
        ApprovalStep step = steps.findById(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval step " + stepId + " not found"));
        ApprovalRequest req = step.getRequest();
        req.getSteps().size(); // initialize within the tx so the controller can map the trail

        if (req.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("This request is already " + req.getStatus().name().toLowerCase());
        }
        if (!step.getApproverEmpId().equals(actingEmpId)) {
            throw new BusinessException("You are not the assigned approver for this step");
        }
        if (step.getStepNo() != req.getCurrentStep() || step.getStatus() != StepStatus.PENDING) {
            throw new BusinessException("This step is not awaiting your decision");
        }

        step.setComment(comment);
        step.setDecidedAt(Instant.now());

        if (!approve) {
            step.setStatus(StepStatus.REJECTED);
            skipRemaining(req);
            req.setStatus(ApprovalStatus.REJECTED);
            req.setResolvedAt(Instant.now());
            requests.save(req);
            handlerFor(req.getSubjectType()).onRejected(req, comment);
            return req;
        }

        step.setStatus(StepStatus.APPROVED);
        if (req.getCurrentStep() >= req.getTotalSteps()) {
            req.setStatus(ApprovalStatus.APPROVED);
            req.setResolvedAt(Instant.now());
            requests.save(req);
            handlerFor(req.getSubjectType()).onApproved(req);
        } else {
            req.setCurrentStep(req.getCurrentStep() + 1);
            requests.save(req);
        }
        return req;
    }

    /** Cancel a still-open flow from the domain side (requester withdrew the underlying request). */
    @Transactional
    public void cancelBySubject(String subjectType, Long subjectId, String reason) {
        requests.findBySubjectTypeAndSubjectId(subjectType, subjectId).ifPresent(req -> {
            if (req.getStatus() == ApprovalStatus.PENDING) {
                skipRemaining(req);
                req.setStatus(ApprovalStatus.CANCELLED);
                req.setResolvedAt(Instant.now());
                requests.save(req);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<ApprovalStep> inbox(Long approverEmpId) {
        // Pending steps assigned to this approver that are the active step of an open request.
        return steps.findByApproverEmpIdAndStatusOrderByIdDesc(approverEmpId, StepStatus.PENDING).stream()
                .filter(s -> {
                    ApprovalRequest r = s.getRequest();
                    return r.getStatus() == ApprovalStatus.PENDING && r.getCurrentStep() == s.getStepNo();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long inboxCount(Long approverEmpId) {
        return inbox(approverEmpId).size();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> myRequests(Long requesterEmpId) {
        List<ApprovalRequest> mine = requests.findByRequesterEmpIdOrderByIdDesc(requesterEmpId);
        mine.forEach(r -> r.getSteps().size()); // initialize step trails within the tx
        return mine;
    }

    private void skipRemaining(ApprovalRequest req) {
        for (ApprovalStep s : req.getSteps()) {
            if (s.getStatus() == StepStatus.PENDING) {
                s.setStatus(StepStatus.SKIPPED);
            }
        }
    }

    private ApprovalOutcomeHandler handlerFor(String subjectType) {
        List<ApprovalOutcomeHandler> handlers = handlersProvider.getIfAvailable(ArrayList::new);
        Map<String, ApprovalOutcomeHandler> byType = handlers.stream()
                .collect(Collectors.toMap(ApprovalOutcomeHandler::subjectType, h -> h, (a, b) -> a));
        ApprovalOutcomeHandler handler = byType.get(subjectType);
        if (handler == null) {
            throw new BusinessException("No approval handler registered for subject type " + subjectType);
        }
        return handler;
    }
}
