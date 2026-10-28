package com.urbantransport.reporting.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dim_bus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DimBusEntity {

    @Id
    @Column(name = "bus_id")
    private UUID busId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(length = 15)
    private String plate;

    @Column(length = 60)
    private String brand;

    @Column(length = 60)
    private String model;

    @Column(name = "manufacture_year")
    private Short manufactureYear;

    private Integer capacity;

    @Column(length = 20)
    private String status;

    @Column(name = "odometer_km", precision = 12, scale = 2)
    private BigDecimal odometerKm;

    @Column(name = "current_driver_id")
    private UUID currentDriverId;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
