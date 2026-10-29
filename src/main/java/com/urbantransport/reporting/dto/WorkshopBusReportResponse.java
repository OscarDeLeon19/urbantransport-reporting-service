package com.urbantransport.reporting.dto;

import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Value
public class WorkshopBusReportResponse {
    List<BusGroup> buses;
    BigDecimal total;

    @Value
    public static class BusGroup {
        UUID busId;
        String plate;
        List<CategoryTotal> categories;
        BigDecimal total;
    }

    @Value
    public static class CategoryTotal {
        UUID categoryId;
        String categoryName;
        long lines;
        BigDecimal amount;
    }
}
