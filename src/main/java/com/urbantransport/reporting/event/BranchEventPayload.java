package com.urbantransport.reporting.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BranchEventPayload(
        UUID branchId,
        String name,
        String code,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean active) {
}
