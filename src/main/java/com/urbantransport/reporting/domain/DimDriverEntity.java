package com.urbantransport.reporting.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "dim_driver")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DimDriverEntity {

    @Id
    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "license_number", length = 30)
    private String licenseNumber;

    @Column(name = "license_type", length = 2)
    private String licenseType;

    @Column(name = "license_expires_on")
    private LocalDate licenseExpiresOn;

    private Boolean active;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
