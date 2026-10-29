package com.urbantransport.reporting.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketPurchasedPayload(
        UUID orderId,
        String channel,
        UUID branchId,
        LocalDate paymentDate,
        BigDecimal total,
        TripInfo trip,
        List<TicketInfo> tickets) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TripInfo(UUID tripId, String originName, String destinationName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TicketInfo(UUID ticketId, Integer seatNumber, String seatLabel, BigDecimal pricePaid) {
    }
}
