package com.urbantransport.reporting.repository;

import com.urbantransport.reporting.domain.FactTicketSaleEntity;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FactTicketSaleSpecificationsTest {

    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ROUTE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final LocalDate TO = LocalDate.now(ZoneId.of("America/Guatemala"));
    private static final LocalDate FROM = TO.minusDays(7);

    @Mock
    private Root<FactTicketSaleEntity> root;

    @Mock
    private CriteriaQuery<Object> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Path<Boolean> cancelledPath;

    @Mock
    private Path<Object> branchPath;

    @Mock
    private Path<Object> routePath;

    @Mock
    private Path<Object> busPath;

    @Mock
    private Path<LocalDate> paymentDatePath;

    @Mock
    private Predicate activePredicate;

    @Mock
    private Predicate branchPredicate;

    @Mock
    private Predicate routePredicate;

    @Mock
    private Predicate busPredicate;

    @Mock
    private Predicate fromPredicate;

    @Mock
    private Predicate toPredicate;

    @Mock
    private Predicate combined;

    @Test
    void allFilters_keepOnlyActiveSalesInRange() {
        doReturn(cancelledPath).when(root).get("cancelled");
        doReturn(branchPath).when(root).get("branchId");
        doReturn(routePath).when(root).get("routeId");
        doReturn(busPath).when(root).get("busId");
        doReturn(paymentDatePath).when(root).get("paymentDate");
        when(cb.isFalse(cancelledPath)).thenReturn(activePredicate);
        when(cb.equal(branchPath, BRANCH_ID)).thenReturn(branchPredicate);
        when(cb.equal(routePath, ROUTE_ID)).thenReturn(routePredicate);
        when(cb.equal(busPath, BUS_ID)).thenReturn(busPredicate);
        when(cb.greaterThanOrEqualTo(paymentDatePath, FROM)).thenReturn(fromPredicate);
        when(cb.lessThanOrEqualTo(paymentDatePath, TO)).thenReturn(toPredicate);
        when(cb.and(activePredicate, branchPredicate, routePredicate, busPredicate, fromPredicate, toPredicate))
                .thenReturn(combined);

        Predicate result = FactTicketSaleSpecifications.activeSales(BRANCH_ID, ROUTE_ID, BUS_ID, FROM, TO)
                .toPredicate(root, query, cb);

        assertSame(combined, result);
    }

    @Test
    void withoutFilters_stillExcludesCancelledSales() {
        doReturn(cancelledPath).when(root).get("cancelled");
        when(cb.isFalse(cancelledPath)).thenReturn(activePredicate);
        when(cb.and(activePredicate)).thenReturn(combined);

        Predicate result = FactTicketSaleSpecifications.activeSales(null, null, null, null, null)
                .toPredicate(root, query, cb);

        assertSame(combined, result);
        verify(root, never()).get("branchId");
        verify(root, never()).get("paymentDate");
    }
}
