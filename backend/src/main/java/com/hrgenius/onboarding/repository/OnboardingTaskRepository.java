package com.hrgenius.onboarding.repository;

import com.hrgenius.onboarding.entity.OnboardingEnums.TaskStatus;
import com.hrgenius.onboarding.entity.OnboardingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OnboardingTaskRepository extends JpaRepository<OnboardingTask, Long> {

    /** Tasks assigned to a person across all plans with the given status, soonest due first. */
    @Query("""
            select t from OnboardingTask t join fetch t.plan p join fetch p.employee
            where t.assignee.id = :empId and t.status = :status
            order by t.dueDate asc, t.id asc
            """)
    List<OnboardingTask> findAssigned(@Param("empId") Long empId, @Param("status") TaskStatus status);
}
