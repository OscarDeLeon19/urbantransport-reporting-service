package com.urbantransport.reporting.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dim_route")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DimRouteEntity {

    @Id
    @Column(name = "route_id")
    private UUID routeId;

    @Column(name = "origin_branch_id")
    private UUID originBranchId;

    @Column(name = "destination_branch_id")
    private UUID destinationBranchId;

    @Column(name = "distance_km", precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "ticket_price", precision = 10, scale = 2)
    private BigDecimal ticketPrice;

    private Boolean deleted;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
