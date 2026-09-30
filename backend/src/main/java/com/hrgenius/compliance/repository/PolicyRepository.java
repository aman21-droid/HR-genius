package com.hrgenius.compliance.repository;

import com.hrgenius.compliance.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    List<Policy> findAllByOrderByTitleAsc();

    List<Policy> findByStatusOrderByTitleAsc(Policy.Status status);

    boolean existsByCodeIgnoreCase(String code);
}
