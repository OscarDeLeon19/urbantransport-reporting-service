package com.urbantransport.reporting.dto;

import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
public class WorkshopCategoryReportResponse {
    List<CategoryGroup> categories;
    BigDecimal total;

    @Value
    public static class CategoryGroup {
        UUID categoryId;
        String categoryName;
        List<Line> lines;
        BigDecimal subtotal;
    }

    @Value
    public static class Line {
        UUID lineId;
        UUID expenseId;
        UUID busId;
        String plate;
        LocalDate serviceDate;
        String description;
        String type;
        BigDecimal amount;
    }
}
