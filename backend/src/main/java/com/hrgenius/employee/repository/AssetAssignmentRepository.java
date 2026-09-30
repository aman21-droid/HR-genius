package com.hrgenius.employee.repository;

import com.hrgenius.employee.entity.AssetAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, Long> {

    /** An employee's custody history, newest first, with the asset fetched in the same query. */
    @EntityGraph(attributePaths = "asset")
    List<AssetAssignment> findByEmployeeIdOrderByAssignedOnDescIdDesc(Long employeeId);

    @EntityGraph(attributePaths = "asset")
    List<AssetAssignment> findByAsset_IdOrderByAssignedOnDescIdDesc(Long assetId);

    Optional<AssetAssignment> findFirstByAsset_IdAndReturnedOnIsNull(Long assetId);
}
