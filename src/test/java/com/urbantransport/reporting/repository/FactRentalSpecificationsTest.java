package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.FactRentalEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FactRentalSpecificationsTest {

    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate TO = LocalDate.now(ZoneId.of("America/Guatemala"));
    private static final LocalDate FROM = TO.minusDays(15);

    @Mock
    private Root<FactRentalEntity> root;

    @Mock
    private CriteriaQuery<Object> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Path<Object> branchPath;

    @Mock
    private Path<LocalDate> paymentDatePath;

    @Mock
    private Predicate branchPredicate;

    @Mock
    private Predicate fromPredicate;

    @Mock
    private Predicate toPredicate;

    @Mock
    private Predicate combined;

    @Test
    void allFilters_useInclusivePaymentDates() {
        doReturn(branchPath).when(root).get("branchId");
        doReturn(paymentDatePath).when(root).get("paymentDate");
        when(cb.equal(branchPath, BRANCH_ID)).thenReturn(branchPredicate);
        when(cb.greaterThanOrEqualTo(paymentDatePath, FROM)).thenReturn(fromPredicate);
        when(cb.lessThanOrEqualTo(paymentDatePath, TO)).thenReturn(toPredicate);
        when(cb.and(branchPredicate, fromPredicate, toPredicate)).thenReturn(combined);

        Predicate result = FactRentalSpecifications.filter(BRANCH_ID, FROM, TO).toPredicate(root, query, cb);

        assertSame(combined, result);
    }

    @Test
    void withoutFilters_returnsEmptyConjunction() {
        when(cb.and()).thenReturn(combined);

        Predicate result = FactRentalSpecifications.filter(null, null, null).toPredicate(root, query, cb);

        assertSame(combined, result);
        verifyNoInteractions(root, query);
    }
}
