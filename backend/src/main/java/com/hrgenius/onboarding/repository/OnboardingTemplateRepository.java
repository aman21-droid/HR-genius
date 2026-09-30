package com.hrgenius.onboarding.repository;

import com.hrgenius.onboarding.entity.OnboardingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OnboardingTemplateRepository extends JpaRepository<OnboardingTemplate, Long> {

    Optional<OnboardingTemplate> findFirstByDefaultTemplateTrueAndActiveTrue();

    List<OnboardingTemplate> findAllByOrderByNameAsc();
}
