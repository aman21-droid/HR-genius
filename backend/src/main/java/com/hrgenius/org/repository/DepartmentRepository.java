package com.hrgenius.org.repository;

import com.hrgenius.org.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.lang.NonNull;

public interface DepartmentRepository extends MasterRepository<Department> {

    /** Fetch-joins the to-one references shown in the department list (avoids N+1). */
    @Override
    @NonNull
    @EntityGraph(attributePaths = {"businessUnit", "costCenter", "parent", "head"})
    Page<Department> findAll(Specification<Department> spec, @NonNull Pageable pageable);
}
