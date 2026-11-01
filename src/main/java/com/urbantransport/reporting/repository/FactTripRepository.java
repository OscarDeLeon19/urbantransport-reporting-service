package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.FactTripEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FactTripRepository extends JpaRepository<FactTripEntity, UUID> {

    List<FactTripEntity> findByBusIdInAndStatusIn(Collection<UUID> busIds, Collection<String> statuses);

    @Query("select t.busId as id, count(t) as total from FactTripEntity t "
            + "where t.busId in :busIds and t.status = :status group by t.busId")
    List<IdCountView> countByBus(@Param("busIds") Collection<UUID> busIds, @Param("status") String status);

    @Query("select t.driverId as id, count(t) as total from FactTripEntity t "
            + "where t.driverId in :driverIds and t.status = :status group by t.driverId")
    List<IdCountView> countByDriver(@Param("driverIds") Collection<UUID> driverIds, @Param("status") String status);
}
