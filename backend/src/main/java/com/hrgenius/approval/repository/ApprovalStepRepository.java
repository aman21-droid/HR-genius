package com.hrgenius.approval.repository;

import com.hrgenius.approval.entity.ApprovalEnums.StepStatus;
import com.hrgenius.approval.entity.ApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {

    /** A given approver's steps in a state (their inbox is approverEmpId + PENDING). */
    List<ApprovalStep> findByApproverEmpIdAndStatusOrderByIdDesc(Long approverEmpId, StepStatus status);

    long countByApproverEmpIdAndStatus(Long approverEmpId, StepStatus status);
}
