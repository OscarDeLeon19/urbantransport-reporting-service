package com.urbantransport.reporting.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"source_type", "source_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LedgerEntryType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerConcept concept;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private LedgerSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
