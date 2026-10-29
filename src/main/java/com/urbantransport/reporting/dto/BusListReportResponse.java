package com.urbantransport.reporting.dto;

import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Value
public class BusListReportResponse {
    List<BusRow> buses;
    long totalTrips;

    @Value
    public static class BusRow {
        UUID busId;
        String plate;
        String brand;
        String model;
        Integer capacity;
        String status;
        UUID currentDriverId;
        String currentDriverName;
        BigDecimal odometerKm;
        long completedTrips;
    }
}
