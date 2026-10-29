package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.FactWorkshopLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface FactWorkshopLineRepository extends JpaRepository<FactWorkshopLineEntity, UUID>,
        JpaSpecificationExecutor<FactWorkshopLineEntity> {
}
