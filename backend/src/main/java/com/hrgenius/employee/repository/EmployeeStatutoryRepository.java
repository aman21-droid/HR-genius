package com.hrgenius.employee.repository;

import com.hrgenius.employee.entity.EmployeeStatutory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeStatutoryRepository extends JpaRepository<EmployeeStatutory, Long> {
}
