package com.urbantransport.reporting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fact_workshop_line")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FactWorkshopLineEntity {

    @Id
    @Column(name = "line_id")
    private UUID lineId;

    @Column(name = "expense_id")
    private UUID expenseId;

    @Column(name = "bus_id")
    private UUID busId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "service_date")
    private LocalDate serviceDate;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "category_name", length = 80)
    private String categoryName;

    @Column(length = 10)
    private String type;

    @Column(length = 255)
    private String description;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;
}
