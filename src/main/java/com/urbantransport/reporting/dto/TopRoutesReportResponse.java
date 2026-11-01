package com.urbantransport.reporting.dto;

import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Value
public class TopRoutesReportResponse {
    List<RouteDemand> routes;
    long totalTickets;
    BigDecimal totalIncome;

    @Value
    public static class RouteDemand {
        UUID routeId;
        UUID originBranchId;
        String originName;
        UUID destinationBranchId;
        String destinationName;
        BigDecimal distanceKm;
        BigDecimal ticketPrice;
        long trips;
        long ticketsSold;
        BigDecimal income;
    }
}
