package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.FactDepreciationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface FactDepreciationRepository extends JpaRepository<FactDepreciationEntity, UUID>,
        JpaSpecificationExecutor<FactDepreciationEntity> {
}
