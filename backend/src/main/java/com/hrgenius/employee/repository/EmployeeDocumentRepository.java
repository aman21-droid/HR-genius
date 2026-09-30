package com.hrgenius.employee.repository;

import com.hrgenius.employee.entity.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {

    List<EmployeeDocument> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    /** Documents already expired or expiring on/before {@code until}, soonest first. */
    @Query("select d from EmployeeDocument d where d.expiryDate is not null and d.expiryDate <= :until "
            + "order by d.expiryDate asc")
    List<EmployeeDocument> findExpiringOnOrBefore(LocalDate until);
}
