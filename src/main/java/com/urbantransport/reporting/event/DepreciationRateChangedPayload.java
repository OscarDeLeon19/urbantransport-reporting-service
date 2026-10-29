package com.urbantransport.reporting.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DepreciationRateChangedPayload(UUID rateId, BigDecimal amountPerKm, Instant effectiveFrom) {
}
