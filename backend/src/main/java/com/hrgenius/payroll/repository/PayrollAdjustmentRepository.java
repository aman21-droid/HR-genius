package com.hrgenius.payroll.repository;

import com.hrgenius.payroll.entity.PayrollAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment, Long> {

    @Query("""
            select a from PayrollAdjustment a join fetch a.employee
            where a.run.id = :runId order by a.id asc
            """)
    List<PayrollAdjustment> findByRun(@Param("runId") Long runId);
}
