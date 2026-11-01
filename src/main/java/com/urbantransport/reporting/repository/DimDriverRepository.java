package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.DimDriverEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DimDriverRepository extends JpaRepository<DimDriverEntity, UUID> {

    List<DimDriverEntity> findByBranchIdOrderByFullNameAsc(UUID branchId);
}
