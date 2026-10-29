package com.urbantransport.reporting.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketCancelledPayload(
        UUID ticketId,
        UUID orderId,
        UUID tripId,
        Integer seatNumber,
        String seatLabel,
        String channel,
        UUID branchId,
        BigDecimal pricePaid,
        String refundMethod) {
}
