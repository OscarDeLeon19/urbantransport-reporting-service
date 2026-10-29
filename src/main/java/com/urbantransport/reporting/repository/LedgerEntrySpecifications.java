package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerEntryType;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LedgerEntrySpecifications {

    public static Specification<LedgerEntryEntity> filter(LedgerEntryType type, UUID branchId, LocalDate from,
                                                          LocalDate to) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredOn"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredOn"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
