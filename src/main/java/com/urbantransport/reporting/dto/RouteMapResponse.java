package com.urbantransport.reporting.dto;

import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Value
public class RouteMapResponse {
    UUID branchId;
    String branchName;
    List<RouteLine> routes;
    String message;

    @Value
    public static class RouteLine {
        UUID routeId;
        MapPoint origin;
        MapPoint destination;
        BigDecimal distanceKm;
        BigDecimal ticketPrice;
    }

    @Value
    public static class MapPoint {
        UUID branchId;
        String name;
        BigDecimal latitude;
        BigDecimal longitude;
    }
}
