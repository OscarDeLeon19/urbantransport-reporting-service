package com.urbantransport.reporting.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RentalEventPayload(
        UUID rentalId,
        String channel,
        UUID branchId,
        String customerNit,
        String originText,
        String destinationText,
        LocalDate departureDate,
        LocalDate returnDate,
        Integer passengers,
        BigDecimal distanceKm,
        BigDecimal confirmedPrice,
        LocalDate paymentDate,
        String status) {
}
