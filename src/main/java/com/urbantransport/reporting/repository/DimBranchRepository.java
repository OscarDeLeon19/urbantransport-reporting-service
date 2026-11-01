package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.DimBranchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DimBranchRepository extends JpaRepository<DimBranchEntity, UUID> {
}
