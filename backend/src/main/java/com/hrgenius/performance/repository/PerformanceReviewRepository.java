package com.hrgenius.performance.repository;

import com.hrgenius.performance.entity.PerformanceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, Long> {

    @Query("""
            select r from PerformanceReview r join fetch r.cycle c join fetch r.employee
            where r.employee.id = :empId order by c.startDate desc
            """)
    List<PerformanceReview> findForEmployee(@Param("empId") Long employeeId);

    @Query("""
            select r from PerformanceReview r join fetch r.cycle c join fetch r.employee e
            where r.reviewer.id = :empId order by c.startDate desc, e.firstName asc
            """)
    List<PerformanceReview> findForReviewer(@Param("empId") Long reviewerEmpId);

    @Query("""
            select r from PerformanceReview r join fetch r.employee e join fetch r.reviewer
            where r.cycle.id = :cycleId order by e.firstName asc, e.lastName asc
            """)
    List<PerformanceReview> findByCycle(@Param("cycleId") Long cycleId);

    boolean existsByCycleIdAndEmployeeId(Long cycleId, Long employeeId);
}
