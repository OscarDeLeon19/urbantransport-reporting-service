package com.urbantransport.reporting.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DepreciationCalculatedPayload(
        UUID tripId,
        UUID busId,
        UUID branchId,
        BigDecimal km,
        UUID rateId,
        BigDecimal rateApplied,
        BigDecimal amount,
        Instant arrivedAt) {
}
