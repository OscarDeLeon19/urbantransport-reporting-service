package com.urbantransport.reporting.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TripEventPayload(
        UUID tripId,
        String type,
        UUID routeId,
        UUID rentalId,
        UUID originBranchId,
        UUID destinationBranchId,
        UUID busId,
        UUID driverId,
        BigDecimal ticketPrice,
        LocalDateTime departureAt,
        Instant actualDepartureAt,
        Instant actualArrivalAt,
        BigDecimal kmTraveled,
        BigDecimal fuelCost,
        BigDecimal driverPay,
        String status) {
}
