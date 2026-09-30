package com.hrgenius.recruitment.repository;

import com.hrgenius.recruitment.entity.JobApplication;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    @Query("""
            select a from JobApplication a join fetch a.candidate
            where a.requisition.id = :reqId order by a.stageChangedAt desc
            """)
    List<JobApplication> findPipeline(@Param("reqId") Long requisitionId);

    @Query("""
            select a from JobApplication a join fetch a.requisition
            where a.candidate.id = :candidateId order by a.id desc
            """)
    List<JobApplication> findByCandidate(@Param("candidateId") Long candidateId);

    boolean existsByRequisitionIdAndCandidateId(Long requisitionId, Long candidateId);

    /** Per-requisition count of applications still in play, for list badges. */
    @Query("""
            select a.requisition.id, count(a) from JobApplication a
            where a.stage not in :terminal group by a.requisition.id
            """)
    List<Object[]> countActiveByRequisition(@Param("terminal") Collection<ApplicationStage> terminal);
}
