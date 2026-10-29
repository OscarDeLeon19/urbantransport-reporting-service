package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.DimDepreciationRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DimDepreciationRateRepository extends JpaRepository<DimDepreciationRateEntity, UUID> {

    Optional<DimDepreciationRateEntity> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(Instant instant);
}
