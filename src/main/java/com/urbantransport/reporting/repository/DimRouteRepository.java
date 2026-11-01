package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.DimRouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DimRouteRepository extends JpaRepository<DimRouteEntity, UUID> {

    List<DimRouteEntity> findByOriginBranchId(UUID originBranchId);
}
