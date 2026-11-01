package com.urbantransport.reporting.dto;

import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
public class BusDepreciationReportResponse {
    BigDecimal amountPerKm;
    Instant rateEffectiveFrom;
    List<BusDepreciation> buses;
    BigDecimal totalKm;
    BigDecimal totalDepreciation;

    @Value
    public static class BusDepreciation {
        UUID busId;
        String plate;
        BigDecimal km;
        BigDecimal amountPerKm;
        BigDecimal accumulated;
    }
}
