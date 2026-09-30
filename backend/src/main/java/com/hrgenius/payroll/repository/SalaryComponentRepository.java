package com.hrgenius.payroll.repository;

import com.hrgenius.payroll.entity.SalaryComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryComponentRepository extends JpaRepository<SalaryComponent, Long> {

    List<SalaryComponent> findAllByOrderBySortOrderAscIdAsc();

    List<SalaryComponent> findByActiveTrueOrderBySortOrderAscIdAsc();

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByCodeIgnoreCase(String code);
}
