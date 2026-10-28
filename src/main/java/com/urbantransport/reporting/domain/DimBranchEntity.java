package com.urbantransport.reporting.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dim_branch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DimBranchEntity {

    @Id
    @Column(name = "branch_id")
    private UUID branchId;

    @Column(length = 100)
    private String name;

    @Column(length = 20)
    private String code;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    private Boolean active;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
