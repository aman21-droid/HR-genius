package com.hrgenius.compliance.repository;

import com.hrgenius.compliance.entity.PolicyAcknowledgement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyAcknowledgementRepository extends JpaRepository<PolicyAcknowledgement, Long> {

    Optional<PolicyAcknowledgement> findByPolicyIdAndEmployeeIdAndVersionNo(Long policyId, Long employeeId, int versionNo);

    List<PolicyAcknowledgement> findByEmployeeId(Long employeeId);

    List<PolicyAcknowledgement> findByPolicyIdAndVersionNo(Long policyId, int versionNo);
}
