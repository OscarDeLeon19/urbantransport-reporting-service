package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.FactTicketSaleEntity;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FactTicketSaleSpecifications {

    public static Specification<FactTicketSaleEntity> activeSales(UUID branchId, UUID routeId, UUID busId,
                                                                  LocalDate from, LocalDate to) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("cancelled")));
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (routeId != null) {
                predicates.add(cb.equal(root.get("routeId"), routeId));
            }
            if (busId != null) {
                predicates.add(cb.equal(root.get("busId"), busId));
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
