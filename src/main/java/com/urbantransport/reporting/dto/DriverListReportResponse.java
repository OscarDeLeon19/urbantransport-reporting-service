package com.urbantransport.reporting.dto;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
public class DriverListReportResponse {
    List<DriverRow> drivers;
    long totalTrips;

    @Value
    public static class DriverRow {
        UUID driverId;
        String licenseNumber;
        String fullName;
        String licenseType;
        LocalDate licenseExpiresOn;
        Boolean active;
        long completedTrips;
    }
}
