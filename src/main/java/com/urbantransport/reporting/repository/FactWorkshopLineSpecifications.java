package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.FactWorkshopLineEntity;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FactWorkshopLineSpecifications {

    public static Specification<FactWorkshopLineEntity> filter(UUID branchId, UUID busId, LocalDate from,
                                                               LocalDate to) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (busId != null) {
                predicates.add(cb.equal(root.get("busId"), busId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("serviceDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("serviceDate"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
