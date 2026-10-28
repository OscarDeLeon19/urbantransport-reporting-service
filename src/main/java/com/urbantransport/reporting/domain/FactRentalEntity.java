package com.urbantransport.reporting.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fact_rental")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FactRentalEntity {

    @Id
    @Column(name = "rental_id")
    private UUID rentalId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(length = 10)
    private String channel;

    @Column(name = "customer_nit", length = 20)
    private String customerNit;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "departure_date")
    private LocalDate departureDate;

    @Column(name = "return_date")
    private LocalDate returnDate;

    private Integer passengers;

    @Column(name = "distance_km", precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Column(length = 20)
    private String status;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
