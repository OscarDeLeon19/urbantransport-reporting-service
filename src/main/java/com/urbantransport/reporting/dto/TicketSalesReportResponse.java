package com.urbantransport.reporting.dto;

import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
public class TicketSalesReportResponse {
    List<TripSales> trips;
    long totalTickets;
    BigDecimal totalIncome;

    @Value
    public static class TripSales {
        UUID tripId;
        UUID routeId;
        String originName;
        String destinationName;
        Instant departureAt;
        UUID busId;
        String plate;
        long ticketsSold;
        BigDecimal income;
    }
}
