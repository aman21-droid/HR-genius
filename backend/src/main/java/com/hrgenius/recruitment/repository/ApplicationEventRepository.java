package com.hrgenius.recruitment.repository;

import com.hrgenius.recruitment.entity.ApplicationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, Long> {

    List<ApplicationEvent> findByApplicationIdOrderByIdDesc(Long applicationId);
}
