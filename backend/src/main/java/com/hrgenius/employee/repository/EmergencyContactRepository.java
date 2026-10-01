package com.hrgenius.employee.repository;

import com.hrgenius.employee.entity.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {

    List<EmergencyContact> findByEmployeeIdOrderByPrimaryDescNameAsc(Long employeeId);

    Optional<EmergencyContact> findByIdAndEmployeeId(Long id, Long employeeId);

    /**
     * Clears the primary flag on an employee's other contacts (only one primary allowed). The flag is
     * bound as a parameter: a literal false is not assignable to the NUMBER(1) column on Oracle/H2.
     */
    @Modifying
    @Query("update EmergencyContact c set c.primary = :off where c.employeeId = :employeeId and c.id <> :keepId")
    void clearPrimary(@Param("employeeId") Long employeeId, @Param("keepId") Long keepId, @Param("off") boolean off);

    default void clearPrimaryExcept(Long employeeId, Long keepId) {
        clearPrimary(employeeId, keepId, false);
    }
}
