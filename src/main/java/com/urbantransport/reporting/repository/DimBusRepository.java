package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.DimBusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DimBusRepository extends JpaRepository<DimBusEntity, UUID> {

    List<DimBusEntity> findByBranchIdOrderByPlateAsc(UUID branchId);

    List<DimBusEntity> findByBranchIdAndStatusOrderByPlateAsc(UUID branchId, String status);
}
