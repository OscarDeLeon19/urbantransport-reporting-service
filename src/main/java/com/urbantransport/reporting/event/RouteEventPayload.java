package com.urbantransport.reporting.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RouteEventPayload(
        UUID routeId,
        UUID originBranchId,
        UUID destinationBranchId,
        BigDecimal distanceKm,
        BigDecimal ticketPrice,
        Boolean deleted) {
}
