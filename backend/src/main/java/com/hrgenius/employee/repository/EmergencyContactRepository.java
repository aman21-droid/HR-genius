package com.hrgenius.employee.repository;

import com.hrgenius.employee.entity.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {

    List<EmergencyContact> findByEmployeeIdOrderByPrimaryDescNameAsc(Long employeeId);

    Optional<EmergencyContact> findByIdAndEmployeeId(Long id, Long employeeId);

    /** Clears the primary flag on an employee's other contacts (only one primary allowed). */
    @Modifying
    @Query("update EmergencyContact c set c.primary = false where c.employeeId = :employeeId and c.id <> :keepId")
    void clearPrimaryExcept(Long employeeId, Long keepId);
}
