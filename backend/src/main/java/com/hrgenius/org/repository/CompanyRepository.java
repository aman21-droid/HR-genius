package com.hrgenius.org.repository;

import com.hrgenius.org.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findFirstByOrderByIdAsc();
}
