package com.hrgenius.performance.repository;

import com.hrgenius.performance.entity.FeedbackNote;
import com.hrgenius.performance.entity.PerformanceEnums.Visibility;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeedbackNoteRepository extends JpaRepository<FeedbackNote, Long> {

    @Query("""
            select f from FeedbackNote f join fetch f.author join fetch f.recipient
            where f.visibility = :v order by f.createdAt desc, f.id desc
            """)
    List<FeedbackNote> findWall(@Param("v") Visibility visibility, Pageable page);

    @Query("""
            select f from FeedbackNote f join fetch f.author join fetch f.recipient t
            where t.id = :empId order by f.createdAt desc, f.id desc
            """)
    List<FeedbackNote> findReceived(@Param("empId") Long employeeId);

    @Query("""
            select f from FeedbackNote f join fetch f.author fr join fetch f.recipient
            where fr.id = :empId order by f.createdAt desc, f.id desc
            """)
    List<FeedbackNote> findGiven(@Param("empId") Long employeeId);

    /** Private feedback about a manager's direct reports, which that manager may read. */
    @Query("""
            select f from FeedbackNote f join fetch f.author join fetch f.recipient t
            where t.manager.id = :managerId
              and f.visibility = com.hrgenius.performance.entity.PerformanceEnums.Visibility.PRIVATE
            order by f.createdAt desc, f.id desc
            """)
    List<FeedbackNote> findPrivateForTeamOf(@Param("managerId") Long managerId);
}
