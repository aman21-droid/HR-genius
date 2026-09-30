package com.hrgenius.payroll.repository;

import com.hrgenius.payroll.entity.PayrollRun.RunStatus;
import com.hrgenius.payroll.entity.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    @Query("""
            select p from Payslip p join fetch p.employee e
            where p.run.id = :runId order by e.employeeCode asc
            """)
    List<Payslip> findByRun(@Param("runId") Long runId);

    /** An employee's payslips from runs in the given statuses (published ones for self-service). */
    @Query("""
            select p from Payslip p join fetch p.run r
            where p.employee.id = :empId and r.status in :statuses order by r.period desc
            """)
    List<Payslip> findForEmployee(@Param("empId") Long employeeId, @Param("statuses") Collection<RunStatus> statuses);

    /** Removes a run's payslips (and, via the bulk delete below, their lines) before recalculation. */
    @Modifying
    @Query("delete from PayslipLine l where l.payslip.id in (select p.id from Payslip p where p.run.id = :runId)")
    void deleteLinesByRun(@Param("runId") Long runId);

    @Modifying
    @Query("delete from Payslip p where p.run.id = :runId")
    void deleteByRun(@Param("runId") Long runId);
}
