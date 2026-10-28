package com.urbantransport.reporting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dim_depreciation_rate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DimDepreciationRateEntity {

    @Id
    @Column(name = "rate_id")
    private UUID rateId;

    @Column(name = "amount_per_km", precision = 10, scale = 4)
    private BigDecimal amountPerKm;

    @Column(name = "effective_from")
    private Instant effectiveFrom;
}
