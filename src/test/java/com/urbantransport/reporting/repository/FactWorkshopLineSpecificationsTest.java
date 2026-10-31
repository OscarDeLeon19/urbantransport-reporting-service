package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.FactWorkshopLineEntity;
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
class FactWorkshopLineSpecificationsTest {

    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BUS_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final LocalDate TO = LocalDate.now(ZoneId.of("America/Guatemala"));
    private static final LocalDate FROM = TO.minusMonths(1);

    @Mock
    private Root<FactWorkshopLineEntity> root;

    @Mock
    private CriteriaQuery<Object> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Path<Object> branchPath;

    @Mock
    private Path<Object> busPath;

    @Mock
    private Path<LocalDate> serviceDatePath;

    @Mock
    private Predicate branchPredicate;

    @Mock
    private Predicate busPredicate;

    @Mock
    private Predicate fromPredicate;

    @Mock
    private Predicate toPredicate;

    @Mock
    private Predicate combined;

    @Test
    void allFilters_useInclusiveServiceDates() {
        doReturn(branchPath).when(root).get("branchId");
        doReturn(busPath).when(root).get("busId");
        doReturn(serviceDatePath).when(root).get("serviceDate");
        when(cb.equal(branchPath, BRANCH_ID)).thenReturn(branchPredicate);
        when(cb.equal(busPath, BUS_ID)).thenReturn(busPredicate);
        when(cb.greaterThanOrEqualTo(serviceDatePath, FROM)).thenReturn(fromPredicate);
        when(cb.lessThanOrEqualTo(serviceDatePath, TO)).thenReturn(toPredicate);
        when(cb.and(branchPredicate, busPredicate, fromPredicate, toPredicate)).thenReturn(combined);

        Predicate result = FactWorkshopLineSpecifications.filter(BRANCH_ID, BUS_ID, FROM, TO)
                .toPredicate(root, query, cb);

        assertSame(combined, result);
    }

    @Test
    void withoutFilters_returnsEmptyConjunction() {
        when(cb.and()).thenReturn(combined);

        Predicate result = FactWorkshopLineSpecifications.filter(null, null, null, null).toPredicate(root, query, cb);

        assertSame(combined, result);
        verifyNoInteractions(root, query);
    }
}
