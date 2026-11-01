package com.urbantransport.reporting.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkshopExpenseRegisteredPayload(
        UUID expenseId,
        UUID busId,
        UUID branchId,
        LocalDate serviceDate,
        BigDecimal total,
        List<Line> lines) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(
            UUID lineId,
            UUID categoryId,
            String categoryName,
            String description,
            String type,
            BigDecimal amount) {
    }
}
