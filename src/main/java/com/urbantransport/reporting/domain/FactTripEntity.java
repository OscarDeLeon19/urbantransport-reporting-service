package com.urbantransport.reporting.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fact_trip")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FactTripEntity {

    @Id
    @Column(name = "trip_id")
    private UUID tripId;

    @Column(length = 10)
    private String type;

    @Column(name = "route_id")
    private UUID routeId;

    @Column(name = "rental_id")
    private UUID rentalId;

    @Column(name = "origin_branch_id")
    private UUID originBranchId;

    @Column(name = "destination_branch_id")
    private UUID destinationBranchId;

    @Column(name = "bus_id")
    private UUID busId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "scheduled_departure")
    private Instant scheduledDeparture;

    @Column(name = "actual_departure_at")
    private Instant actualDepartureAt;

    @Column(name = "actual_arrival_at")
    private Instant actualArrivalAt;

    @Column(name = "km_traveled", precision = 12, scale = 2)
    private BigDecimal kmTraveled;

    @Column(name = "fuel_cost", precision = 10, scale = 2)
    private BigDecimal fuelCost;

    @Column(name = "driver_pay", precision = 10, scale = 2)
    private BigDecimal driverPay;

    @Column(name = "ticket_price", precision = 10, scale = 2)
    private BigDecimal ticketPrice;

    @Column(length = 20)
    private String status;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
