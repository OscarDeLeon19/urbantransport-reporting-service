package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.FactRentalEntity;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FactRentalSpecifications {

    public static Specification<FactRentalEntity> filter(UUID branchId, LocalDate from, LocalDate to) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("paymentDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("paymentDate"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
