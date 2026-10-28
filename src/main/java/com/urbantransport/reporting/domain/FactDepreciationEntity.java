package com.urbantransport.reporting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fact_depreciation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FactDepreciationEntity {

    @Id
    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "bus_id")
    private UUID busId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(precision = 12, scale = 2)
    private BigDecimal km;

    @Column(name = "rate_id")
    private UUID rateId;

    @Column(name = "rate_applied", precision = 10, scale = 4)
    private BigDecimal rateApplied;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "arrived_at")
    private Instant arrivedAt;
}
