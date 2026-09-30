package com.hrgenius.performance.repository;

import com.hrgenius.performance.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {
}
