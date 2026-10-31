package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.FactDepreciationEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FactDepreciationSpecificationsTest {

    private static final List<UUID> BUS_IDS = List.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final Instant FROM = Instant.parse("2026-10-01T06:00:00Z");
    private static final Instant TO_EXCLUSIVE = Instant.parse("2026-11-01T06:00:00Z");

    @Mock
    private Root<FactDepreciationEntity> root;

    @Mock
    private CriteriaQuery<Object> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Path<Object> busPath;

    @Mock
    private Path<Instant> arrivedAtPath;

    @Mock
    private Predicate busPredicate;

    @Mock
    private Predicate fromPredicate;

    @Mock
    private Predicate toPredicate;

    @Mock
    private Predicate combined;

    @Test
    void allFilters_useInclusiveStartAndExclusiveEnd() {
        doReturn(busPath).when(root).get("busId");
        doReturn(arrivedAtPath).when(root).get("arrivedAt");
        when(busPath.in(BUS_IDS)).thenReturn(busPredicate);
        when(cb.greaterThanOrEqualTo(arrivedAtPath, FROM)).thenReturn(fromPredicate);
        when(cb.lessThan(arrivedAtPath, TO_EXCLUSIVE)).thenReturn(toPredicate);
        when(cb.and(busPredicate, fromPredicate, toPredicate)).thenReturn(combined);

        Predicate result = FactDepreciationSpecifications.filter(BUS_IDS, FROM, TO_EXCLUSIVE)
                .toPredicate(root, query, cb);

        assertSame(combined, result);
        verify(cb, never()).lessThanOrEqualTo(arrivedAtPath, TO_EXCLUSIVE);
    }

    @Test
    void withoutDates_filtersOnlyByBuses() {
        doReturn(busPath).when(root).get("busId");
        when(busPath.in(BUS_IDS)).thenReturn(busPredicate);
        when(cb.and(busPredicate)).thenReturn(combined);

        Predicate result = FactDepreciationSpecifications.filter(BUS_IDS, null, null).toPredicate(root, query, cb);

        assertSame(combined, result);
        verify(root, never()).get("arrivedAt");
    }
}
