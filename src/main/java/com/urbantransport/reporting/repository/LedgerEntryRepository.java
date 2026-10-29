package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID>,
        JpaSpecificationExecutor<LedgerEntryEntity> {

    boolean existsBySourceTypeAndSourceId(LedgerSourceType sourceType, UUID sourceId);

    @Modifying
    @Query(value = "update ledger_entries set branch_id = :branchId "
            + "where branch_id is null "
            + "and source_type in ('TRIP_FUEL', 'TRIP_DRIVER_PAY', 'DEPRECIATION') "
            + "and source_id in (select trip_id from fact_trip where bus_id = :busId "
            + "union select trip_id from fact_depreciation where bus_id = :busId)",
            nativeQuery = true)
    int assignBranchToBusTrips(@Param("busId") UUID busId, @Param("branchId") UUID branchId);
}
