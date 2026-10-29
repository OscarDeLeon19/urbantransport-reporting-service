package com.urbantransport.reporting.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DriverEventPayload(
        UUID driverId,
        UUID userId,
        UUID branchId,
        String fullName,
        String licenseNumber,
        String licenseType,
        LocalDate licenseExpiresOn,
        Boolean active) {
}
