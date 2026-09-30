package com.hrgenius.onboarding.repository;

import com.hrgenius.onboarding.entity.OnboardingEnums.PlanStatus;
import com.hrgenius.onboarding.entity.OnboardingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OnboardingPlanRepository extends JpaRepository<OnboardingPlan, Long> {

    @Query("select p from OnboardingPlan p join fetch p.employee order by p.startDate desc, p.id desc")
    List<OnboardingPlan> findAllWithEmployee();

    Optional<OnboardingPlan> findFirstByEmployeeIdOrderByIdDesc(Long employeeId);

    boolean existsByEmployeeIdAndStatus(Long employeeId, PlanStatus status);
}
