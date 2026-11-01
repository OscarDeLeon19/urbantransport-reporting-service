package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.FactTicketSaleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface FactTicketSaleRepository extends JpaRepository<FactTicketSaleEntity, UUID>,
        JpaSpecificationExecutor<FactTicketSaleEntity> {

    @Modifying
    @Query("update FactTicketSaleEntity s set s.routeId = :routeId, s.busId = :busId where s.tripId = :tripId")
    int syncTrip(@Param("tripId") UUID tripId, @Param("routeId") UUID routeId, @Param("busId") UUID busId);
}
