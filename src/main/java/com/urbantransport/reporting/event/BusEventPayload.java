package com.urbantransport.reporting.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BusEventPayload(
        UUID busId,
        UUID branchId,
        String plate,
        String brand,
        String model,
        Short manufactureYear,
        Integer capacity,
        String status,
        BigDecimal odometerKm) {
}
