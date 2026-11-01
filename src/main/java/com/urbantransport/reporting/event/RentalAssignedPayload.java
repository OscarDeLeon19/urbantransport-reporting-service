package com.urbantransport.reporting.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RentalAssignedPayload(UUID rentalId, UUID branchId, UUID busId) {
}
