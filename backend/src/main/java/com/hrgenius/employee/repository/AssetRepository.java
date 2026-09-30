package com.hrgenius.employee.repository;

import com.hrgenius.employee.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AssetRepository extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {

    boolean existsByAssetTagIgnoreCase(String assetTag);

    boolean existsByAssetTagIgnoreCaseAndIdNot(String assetTag, Long id);

    long countByCurrentEmployeeId(Long employeeId);
}
