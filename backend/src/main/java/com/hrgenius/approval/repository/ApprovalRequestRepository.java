package com.hrgenius.approval.repository;

import com.hrgenius.approval.entity.ApprovalEnums.ApprovalStatus;
import com.hrgenius.approval.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    Optional<ApprovalRequest> findBySubjectTypeAndSubjectId(String subjectType, Long subjectId);

    List<ApprovalRequest> findByRequesterEmpIdOrderByIdDesc(Long requesterEmpId);

    List<ApprovalRequest> findByStatusAndRequesterEmpId(ApprovalStatus status, Long requesterEmpId);
}
