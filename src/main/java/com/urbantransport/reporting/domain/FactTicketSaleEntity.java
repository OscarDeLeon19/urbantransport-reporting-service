package com.urbantransport.reporting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fact_ticket_sale")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FactTicketSaleEntity {

    @Id
    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "route_id")
    private UUID routeId;

    @Column(name = "bus_id")
    private UUID busId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(length = 20)
    private String channel;

    @Column(name = "seat_number")
    private Integer seatNumber;

    @Column(name = "price_paid", precision = 10, scale = 2)
    private BigDecimal pricePaid;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Builder.Default
    @Column(nullable = false)
    private boolean cancelled = false;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}
