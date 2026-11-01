package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.FactDepreciationEntity;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FactDepreciationSpecifications {

    public static Specification<FactDepreciationEntity> filter(Collection<UUID> busIds, Instant from,
                                                               Instant toExclusive) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("busId").in(busIds));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("arrivedAt"), from));
            }
            if (toExclusive != null) {
                predicates.add(cb.lessThan(root.get("arrivedAt"), toExclusive));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
