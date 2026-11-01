package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerEntryType;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerEntrySpecificationsTest {

    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate TO = LocalDate.now(ZoneId.of("America/Guatemala"));
    private static final LocalDate FROM = TO.minusDays(30);

    @Mock
    private Root<LedgerEntryEntity> root;

    @Mock
    private CriteriaQuery<Object> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Path<Object> typePath;

    @Mock
    private Path<Object> branchPath;

    @Mock
    private Path<LocalDate> occurredOnPath;

    @Mock
    private Predicate typePredicate;

    @Mock
    private Predicate branchPredicate;

    @Mock
    private Predicate fromPredicate;

    @Mock
    private Predicate toPredicate;

    @Mock
    private Predicate combined;

    @Test
    void allFilters_useInclusiveDates() {
        doReturn(typePath).when(root).get("type");
        doReturn(branchPath).when(root).get("branchId");
        doReturn(occurredOnPath).when(root).get("occurredOn");
        when(cb.equal(typePath, LedgerEntryType.INCOME)).thenReturn(typePredicate);
        when(cb.equal(branchPath, BRANCH_ID)).thenReturn(branchPredicate);
        when(cb.greaterThanOrEqualTo(occurredOnPath, FROM)).thenReturn(fromPredicate);
        when(cb.lessThanOrEqualTo(occurredOnPath, TO)).thenReturn(toPredicate);
        when(cb.and(typePredicate, branchPredicate, fromPredicate, toPredicate)).thenReturn(combined);

        Predicate result = LedgerEntrySpecifications.filter(LedgerEntryType.INCOME, BRANCH_ID, FROM, TO)
                .toPredicate(root, query, cb);

        assertSame(combined, result);
    }

    @Test
    void withoutFilters_returnsEmptyConjunction() {
        when(cb.and()).thenReturn(combined);

        Predicate result = LedgerEntrySpecifications.filter(null, null, null, null).toPredicate(root, query, cb);

        assertSame(combined, result);
        verifyNoInteractions(root, query);
    }

    @Test
    void onlyType_filtersByType() {
        doReturn(typePath).when(root).get("type");
        when(cb.equal(typePath, LedgerEntryType.EXPENSE)).thenReturn(typePredicate);
        when(cb.and(typePredicate)).thenReturn(combined);

        Predicate result = LedgerEntrySpecifications.filter(LedgerEntryType.EXPENSE, null, null, null)
                .toPredicate(root, query, cb);

        assertSame(combined, result);
        verify(root, never()).get("branchId");
        verify(root, never()).get("occurredOn");
    }
}
