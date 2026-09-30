package com.hrgenius.recruitment.repository;

import com.hrgenius.recruitment.entity.Offer;
import com.hrgenius.recruitment.entity.RecruitmentEnums.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    List<Offer> findByApplicationIdOrderByIdDesc(Long applicationId);

    boolean existsByApplicationIdAndStatusIn(Long applicationId, Collection<OfferStatus> statuses);
}
