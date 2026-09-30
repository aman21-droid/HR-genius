package com.hrgenius.recruitment.repository;

import com.hrgenius.recruitment.entity.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {

    /** A panelist's interviews (newest first), with the candidate and role fetched for display. */
    @Query("""
            select f from InterviewFeedback f
              join fetch f.interview i
              join fetch i.application a
              join fetch a.candidate
              join fetch a.requisition
            where f.interviewer.id = :empId
            order by i.scheduledAt desc
            """)
    List<InterviewFeedback> findForInterviewer(@Param("empId") Long interviewerEmpId);

    Optional<InterviewFeedback> findByInterviewIdAndInterviewerId(Long interviewId, Long interviewerEmpId);

    /** True if the employee sits on any interview panel for this candidate (grants resume access). */
    @Query("""
            select count(f) from InterviewFeedback f
            where f.interviewer.id = :empId and f.interview.application.candidate.id = :candidateId
            """)
    long countPanelSeats(@Param("empId") Long empId, @Param("candidateId") Long candidateId);
}
