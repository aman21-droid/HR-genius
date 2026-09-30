package com.hrgenius.employee.repository;

import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    /** Directory/list query: fetch-joins every to-one shown in a row, so a page is one SELECT. */
    @Override
    @NonNull
    @EntityGraph(attributePaths = {"department", "designation", "grade", "location", "manager"})
    Page<Employee> findAll(Specification<Employee> spec, @NonNull Pageable pageable);

    /** Same graph for unpaged use (Excel export). */
    @Override
    @NonNull
    @EntityGraph(attributePaths = {"department", "designation", "grade", "location", "manager"})
    List<Employee> findAll(Specification<Employee> spec);

    @EntityGraph(attributePaths = {"department", "designation", "grade", "location", "manager",
            "businessUnit", "costCenter"})
    Optional<Employee> findWithDetailsById(Long id);

    Optional<Employee> findByWorkEmailIgnoreCase(String workEmail);

    Optional<Employee> findByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByWorkEmailIgnoreCase(String workEmail);

    boolean existsByWorkEmailIgnoreCaseAndIdNot(String workEmail, Long id);

    long countByManager_IdAndStatusNot(Long managerId, EmployeeStatus status);

    /** Next value of the employee-code sequence (Oracle; also valid in H2's Oracle mode). */
    @Query(value = "SELECT employee_code_seq.NEXTVAL FROM dual", nativeQuery = true)
    Long nextEmployeeCodeNumber();

    /** Employees on payroll for a period: joined by its end, not exited before its start, with a CTC. */
    @Query("""
            select e from Employee e
            where e.dateOfJoining <= :end and (e.exitDate is null or e.exitDate >= :start)
              and e.annualCtc is not null and e.annualCtc > 0
            order by e.employeeCode asc
            """)
    List<Employee> findPayrollEligible(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** (id, managerId) pairs for current employees, used to walk reporting trees in memory. */
    @Query("select e.id as id, m.id as managerId from Employee e left join e.manager m where e.status <> :exited")
    List<ManagerLink> findManagerLinks(EmployeeStatus exited);

    /** Lightweight rows for the org chart; left joins so unplaced employees still appear. */
    @Query("""
            select e.id as id, e.employeeCode as employeeCode, e.firstName as firstName, e.lastName as lastName,
                   ds.name as designation, d.name as department, l.name as location, m.id as managerId
            from Employee e
              left join e.designation ds
              left join e.department d
              left join e.location l
              left join e.manager m
            where e.status <> :exited
            order by e.firstName, e.lastName
            """)
    List<OrgChartRow> findOrgChartRows(EmployeeStatus exited);

    interface ManagerLink {
        Long getId();

        Long getManagerId();
    }

    interface OrgChartRow {
        Long getId();

        String getEmployeeCode();

        String getFirstName();

        String getLastName();

        String getDesignation();

        String getDepartment();

        String getLocation();

        Long getManagerId();
    }
}
