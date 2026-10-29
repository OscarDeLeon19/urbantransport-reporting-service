package com.urbantransport.reporting.dto;

import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
public class RentalReportResponse {
    List<RentalRow> rentals;
    BigDecimal total;

    @Value
    public static class RentalRow {
        UUID rentalId;
        String channel;
        String customerNit;
        String originText;
        String destinationText;
        LocalDate departureDate;
        LocalDate returnDate;
        LocalDate paymentDate;
        UUID busId;
        String plate;
        BigDecimal amount;
    }
}
