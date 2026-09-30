package com.hrgenius.recruitment.repository;

import com.hrgenius.recruitment.entity.JobRequisition;
import com.hrgenius.recruitment.entity.RecruitmentEnums.RequisitionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface JobRequisitionRepository extends JpaRepository<JobRequisition, Long>,
        JpaSpecificationExecutor<JobRequisition> {

    @Query(value = "SELECT requisition_code_seq.NEXTVAL FROM dual", nativeQuery = true)
    Long nextCodeNumber();

    Optional<JobRequisition> findByReqCode(String reqCode);

    /** Jobs shown on the public careers page. */
    List<JobRequisition> findByStatusAndPublishOnCareersTrueOrderByOpenedAtDesc(RequisitionStatus status);
}
