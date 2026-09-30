package com.hrgenius.org.repository;

import com.hrgenius.org.entity.MasterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/** Shared queries for every org master type. */
@NoRepositoryBean
public interface MasterRepository<E extends MasterEntity>
        extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    List<E> findAllByActiveTrueOrderByNameAsc();
}
