package com.hrgenius.performance.repository;

import com.hrgenius.performance.entity.ReviewCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, Long> {

    List<ReviewCycle> findAllByOrderByStartDateDesc();
}
