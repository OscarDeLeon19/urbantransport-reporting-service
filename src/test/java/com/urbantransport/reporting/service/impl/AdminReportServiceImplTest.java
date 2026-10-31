package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.DimBranchEntity;
import com.urbantransport.reporting.domain.DimBusEntity;
import com.urbantransport.reporting.domain.DimRouteEntity;
import com.urbantransport.reporting.domain.FactTicketSaleEntity;
import com.urbantransport.reporting.domain.FactWorkshopLineEntity;
import com.urbantransport.reporting.domain.LedgerConcept;
import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerEntryType;
import com.urbantransport.reporting.domain.LedgerSourceType;
import com.urbantransport.reporting.dto.LedgerReportResponse;
import com.urbantransport.reporting.dto.ProfitReportResponse;
import com.urbantransport.reporting.dto.RouteMapResponse;
import com.urbantransport.reporting.dto.TopRoutesReportResponse;
import com.urbantransport.reporting.dto.WorkshopCategoryReportResponse;
import com.urbantransport.reporting.exception.ApiException;
import com.urbantransport.reporting.exception.ErrorCodes;
import com.urbantransport.reporting.repository.DimBranchRepository;
import com.urbantransport.reporting.repository.DimBusRepository;
import com.urbantransport.reporting.repository.DimRouteRepository;
import com.urbantransport.reporting.repository.FactTicketSaleRepository;
import com.urbantransport.reporting.repository.FactWorkshopLineRepository;
import com.urbantransport.reporting.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceImplTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Guatemala"));
    private static final UUID ANTIGUA_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AVILA_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ZACAPA_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID UNNAMED_ID = UUID.fromString("00000000-0000-0000-0000-000000000044");
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000055");
    private static final UUID ROUTE_1 = UUID.fromString("66666666-6666-6666-6666-666666666661");
    private static final UUID ROUTE_2 = UUID.fromString("66666666-6666-6666-6666-666666666662");
    private static final UUID ROUTE_3 = UUID.fromString("66666666-6666-6666-6666-666666666663");
    private static final UUID ROUTE_4 = UUID.fromString("66666666-6666-6666-6666-666666666664");
    private static final UUID ROUTE_5 = UUID.fromString("66666666-6666-6666-6666-666666666665");
    private static final UUID TRIP_1 = UUID.fromString("77777777-7777-7777-7777-777777777771");
    private static final UUID TRIP_2 = UUID.fromString("77777777-7777-7777-7777-777777777772");
    private static final UUID TRIP_3 = UUID.fromString("77777777-7777-7777-7777-777777777773");
    private static final UUID BUS_1 = UUID.fromString("88888888-8888-8888-8888-888888888881");
    private static final UUID BUS_2 = UUID.fromString("88888888-8888-8888-8888-888888888882");
    private static final UUID MOTOR_ID = UUID.fromString("99999999-9999-9999-9999-999999999991");
    private static final UUID BRAKES_ID = UUID.fromString("99999999-9999-9999-9999-999999999992");
    private static final UUID UNNAMED_CATEGORY_ID = UUID.fromString("99999999-9999-9999-9999-999999999993");

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private FactTicketSaleRepository factTicketSaleRepository;

    @Mock
    private FactWorkshopLineRepository factWorkshopLineRepository;

    @Mock
    private DimBranchRepository dimBranchRepository;

    @Mock
    private DimRouteRepository dimRouteRepository;

    @Mock
    private DimBusRepository dimBusRepository;

    private AdminReportServiceImpl adminReportService;

    @BeforeEach
    void setUp() {
        adminReportService = new AdminReportServiceImpl(ledgerEntryRepository, factTicketSaleRepository,
                factWorkshopLineRepository, dimBranchRepository, dimRouteRepository, dimBusRepository);
    }

    @Nested
    class Income {

        @Test
        void entries_areGroupedByBranchInSpanishAlphabeticalOrderWithUnassignedLast() {
            when(dimBranchRepository.findAll()).thenReturn(branches());
            when(ledgerEntryRepository.findAll(ArgumentMatchers.<Specification<LedgerEntryEntity>>any(),
                    any(Sort.class))).thenReturn(List.of(
                    entry(ZACAPA_ID, LedgerEntryType.INCOME, "300.00"),
                    entry(null, LedgerEntryType.INCOME, "5.00"),
                    entry(ANTIGUA_ID, LedgerEntryType.INCOME, "100.00"),
                    entry(AVILA_ID, LedgerEntryType.INCOME, "200.00"),
                    entry(ANTIGUA_ID, LedgerEntryType.INCOME, "-50.00"),
                    entry(UNKNOWN_ID, LedgerEntryType.INCOME, "10.00")));

            LedgerReportResponse report = adminReportService.income(TODAY.minusDays(30), TODAY, null);

            assertEquals(LedgerEntryType.INCOME, report.getType());
            List<LedgerReportResponse.BranchGroup> groups = report.getBranches();
            assertEquals(List.of(UNKNOWN_ID.toString(), "Antigua", "Ávila", "Zacapa", "Sin sucursal asignada"),
                    groups.stream().map(LedgerReportResponse.BranchGroup::getBranchName).toList());
            LedgerReportResponse.BranchGroup antigua = groups.get(1);
            assertEquals(ANTIGUA_ID, antigua.getBranchId());
            assertEquals(2, antigua.getEntries().size());
            assertEquals(new BigDecimal("50.00"), antigua.getSubtotal());
            assertNull(groups.get(4).getBranchId());
            assertEquals(new BigDecimal("565.00"), report.getTotal());
            LedgerReportResponse.Entry first = antigua.getEntries().getFirst();
            assertEquals(LedgerConcept.TICKET_SALE, first.getConcept());
            assertEquals(LedgerSourceType.TICKET_SALE, first.getSourceType());
            assertEquals(TODAY, first.getDate());
            assertEquals("Venta", first.getDescription());
        }

        @Test
        void entries_areRequestedOrderedByDateAndCreation() {
            when(dimBranchRepository.findAll()).thenReturn(List.of());
            when(ledgerEntryRepository.findAll(ArgumentMatchers.<Specification<LedgerEntryEntity>>any(),
                    any(Sort.class))).thenReturn(List.of());

            adminReportService.income(null, null, ANTIGUA_ID);

            ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
            verify(ledgerEntryRepository).findAll(ArgumentMatchers.<Specification<LedgerEntryEntity>>any(),
                    sortCaptor.capture());
            assertEquals(Sort.by("occurredOn", "createdAt"), sortCaptor.getValue());
        }

        @Test
        void sameDayRange_isAccepted() {
            when(dimBranchRepository.findAll()).thenReturn(List.of());
            when(ledgerEntryRepository.findAll(ArgumentMatchers.<Specification<LedgerEntryEntity>>any(),
                    any(Sort.class))).thenReturn(List.of());

            LedgerReportResponse report = adminReportService.income(TODAY, TODAY, null);

            assertTrue(report.getBranches().isEmpty());
        }

        @Test
        void invalidRange_throwsBadRequest() {
            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE,
                    () -> adminReportService.income(TODAY, TODAY.minusDays(1), null));

            verifyNoInteractions(ledgerEntryRepository, dimBranchRepository);
        }
    }

    @Nested
    class Expenses {

        @Test
        void withoutEntries_returnsEmptyExpenseReportWithZeroTotal() {
            when(dimBranchRepository.findAll()).thenReturn(branches());
            when(ledgerEntryRepository.findAll(ArgumentMatchers.<Specification<LedgerEntryEntity>>any(),
                    any(Sort.class))).thenReturn(List.of());

            LedgerReportResponse report = adminReportService.expenses(null, null, null);

            assertEquals(LedgerEntryType.EXPENSE, report.getType());
            assertTrue(report.getBranches().isEmpty());
            assertEquals(new BigDecimal("0.00"), report.getTotal());
        }

        @Test
        void invalidRange_throwsBadRequest() {
            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE,
                    () -> adminReportService.expenses(TODAY, TODAY.minusDays(1), null));

            verifyNoInteractions(ledgerEntryRepository);
        }
    }

    @Nested
    class Profit {

        @Test
        void withoutBranchFilter_listsEveryBranchAlphabeticallyWithNetAndTotals() {
            when(dimBranchRepository.findAll()).thenReturn(branches());
            when(ledgerEntryRepository.findAll(ArgumentMatchers.<Specification<LedgerEntryEntity>>any()))
                    .thenReturn(List.of(
                            entry(ANTIGUA_ID, LedgerEntryType.INCOME, "500.00"),
                            entry(ANTIGUA_ID, LedgerEntryType.EXPENSE, "200.00"),
                            entry(ZACAPA_ID, LedgerEntryType.EXPENSE, "100.00"),
                            entry(null, LedgerEntryType.INCOME, "30.00")));

            ProfitReportResponse report = adminReportService.profit(null, null, null);

            List<ProfitReportResponse.BranchProfit> rows = report.getBranches();
            assertEquals(List.of(UNNAMED_ID.toString(), "Antigua", "Ávila", "Zacapa", "Sin sucursal asignada"),
                    rows.stream().map(ProfitReportResponse.BranchProfit::getBranchName).toList());
            assertProfit(rows.get(1), "500.00", "200.00", "300.00");
            assertProfit(rows.get(2), "0.00", "0.00", "0.00");
            assertProfit(rows.get(3), "0.00", "100.00", "-100.00");
            assertProfit(rows.get(4), "30.00", "0.00", "30.00");
            assertEquals(new BigDecimal("530.00"), report.getTotalIncome());
            assertEquals(new BigDecimal("300.00"), report.getTotalExpense());
            assertEquals(new BigDecimal("230.00"), report.getTotalNet());
        }

        @Test
        void withBranchFilter_listsOnlyThatBranch() {
            when(dimBranchRepository.findAll()).thenReturn(branches());
            when(ledgerEntryRepository.findAll(ArgumentMatchers.<Specification<LedgerEntryEntity>>any()))
                    .thenReturn(List.of(entry(AVILA_ID, LedgerEntryType.INCOME, "80.00")));

            ProfitReportResponse report = adminReportService.profit(TODAY.minusDays(7), TODAY, AVILA_ID);

            assertEquals(1, report.getBranches().size());
            assertEquals("Ávila", report.getBranches().getFirst().getBranchName());
            assertProfit(report.getBranches().getFirst(), "80.00", "0.00", "80.00");
        }

        @Test
        void invalidRange_throwsBadRequest() {
            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE,
                    () -> adminReportService.profit(TODAY, TODAY.minusDays(1), null));

            verifyNoInteractions(ledgerEntryRepository);
        }
    }

    @Nested
    class TopRoutes {

        @Test
        void routes_areOrderedByTicketsThenIncomeIgnoringSalesWithoutRoute() {
            when(factTicketSaleRepository.findAll(ArgumentMatchers.<Specification<FactTicketSaleEntity>>any()))
                    .thenReturn(List.of(
                            sale(ROUTE_1, TRIP_1, "50.00"),
                            sale(ROUTE_1, TRIP_1, "50.00"),
                            sale(ROUTE_1, TRIP_2, "50.00"),
                            sale(ROUTE_2, TRIP_3, "60.00"),
                            sale(ROUTE_2, TRIP_3, "60.00"),
                            sale(ROUTE_2, TRIP_3, "60.00"),
                            sale(ROUTE_3, TRIP_3, "500.00"),
                            sale(ROUTE_3, TRIP_3, null),
                            sale(null, TRIP_3, "999.00")));
            when(dimRouteRepository.findAllById(any())).thenReturn(List.of(
                    route(ROUTE_1, ANTIGUA_ID, ZACAPA_ID, false),
                    route(ROUTE_2, ZACAPA_ID, ANTIGUA_ID, false)));
            when(dimBranchRepository.findAll()).thenReturn(branches());

            TopRoutesReportResponse report = adminReportService.topRoutes(null, null);

            List<TopRoutesReportResponse.RouteDemand> rows = report.getRoutes();
            assertEquals(List.of(ROUTE_2, ROUTE_1, ROUTE_3),
                    rows.stream().map(TopRoutesReportResponse.RouteDemand::getRouteId).toList());
            TopRoutesReportResponse.RouteDemand route1 = rows.get(1);
            assertEquals("Antigua", route1.getOriginName());
            assertEquals("Zacapa", route1.getDestinationName());
            assertEquals(new BigDecimal("120.50"), route1.getDistanceKm());
            assertEquals(new BigDecimal("45.00"), route1.getTicketPrice());
            assertEquals(2, route1.getTrips());
            assertEquals(3, route1.getTicketsSold());
            assertEquals(new BigDecimal("150.00"), route1.getIncome());
            TopRoutesReportResponse.RouteDemand unknown = rows.get(2);
            assertNull(unknown.getOriginBranchId());
            assertNull(unknown.getOriginName());
            assertNull(unknown.getDestinationName());
            assertNull(unknown.getDistanceKm());
            assertEquals(2, unknown.getTicketsSold());
            assertEquals(new BigDecimal("500.00"), unknown.getIncome());
            assertEquals(8, report.getTotalTickets());
            assertEquals(new BigDecimal("830.00"), report.getTotalIncome());
        }

        @Test
        void invalidRange_throwsBadRequest() {
            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE,
                    () -> adminReportService.topRoutes(TODAY, TODAY.minusDays(1)));

            verifyNoInteractions(factTicketSaleRepository);
        }
    }

    @Nested
    class WorkshopByCategory {

        @Test
        void lines_areGroupedByCategoryNameWithPlatesAndTotals() {
            when(factWorkshopLineRepository.findAll(ArgumentMatchers.<Specification<FactWorkshopLineEntity>>any(),
                    any(Sort.class))).thenReturn(List.of(
                    line(MOTOR_ID, "Motor", BUS_1, "300.00"),
                    line(BRAKES_ID, "Frenos", BUS_2, "50.00"),
                    line(MOTOR_ID, "Motor", BUS_2, "100.00"),
                    line(UNNAMED_CATEGORY_ID, null, null, "10.00")));
            when(dimBusRepository.findAllById(any())).thenReturn(List.of(
                    DimBusEntity.builder().busId(BUS_1).plate("P-111AAA").build(),
                    DimBusEntity.builder().busId(BUS_2).build()));

            WorkshopCategoryReportResponse report = adminReportService.workshopByCategory(TODAY.minusDays(30), TODAY,
                    ANTIGUA_ID, null);

            List<WorkshopCategoryReportResponse.CategoryGroup> categories = report.getCategories();
            assertEquals(3, categories.size());
            assertEquals("Frenos", categories.get(0).getCategoryName());
            assertEquals("Motor", categories.get(1).getCategoryName());
            assertNull(categories.get(2).getCategoryName());
            WorkshopCategoryReportResponse.CategoryGroup motor = categories.get(1);
            assertEquals(MOTOR_ID, motor.getCategoryId());
            assertEquals(new BigDecimal("400.00"), motor.getSubtotal());
            assertEquals("P-111AAA", motor.getLines().get(0).getPlate());
            assertNull(motor.getLines().get(1).getPlate());
            WorkshopCategoryReportResponse.Line motorLine = motor.getLines().getFirst();
            assertEquals(TODAY, motorLine.getServiceDate());
            assertEquals("LABOR", motorLine.getType());
            assertEquals("Reparación", motorLine.getDescription());
            assertEquals(new BigDecimal("460.00"), report.getTotal());
        }

        @Test
        void lines_areRequestedOrderedByServiceDate() {
            when(factWorkshopLineRepository.findAll(ArgumentMatchers.<Specification<FactWorkshopLineEntity>>any(),
                    any(Sort.class))).thenReturn(List.of());
            when(dimBusRepository.findAllById(any())).thenReturn(List.of());

            WorkshopCategoryReportResponse report = adminReportService.workshopByCategory(null, null, null, BUS_1);

            ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
            verify(factWorkshopLineRepository).findAll(
                    ArgumentMatchers.<Specification<FactWorkshopLineEntity>>any(), sortCaptor.capture());
            assertEquals(Sort.by("serviceDate", "expenseId"), sortCaptor.getValue());
            assertTrue(report.getCategories().isEmpty());
            assertEquals(new BigDecimal("0.00"), report.getTotal());
        }

        @Test
        void invalidRange_throwsBadRequest() {
            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE,
                    () -> adminReportService.workshopByCategory(TODAY, TODAY.minusDays(1), null, null));

            verifyNoInteractions(factWorkshopLineRepository);
        }
    }

    @Nested
    class RouteMap {

        @Test
        void activeRoutes_comeWithCoordinatesSortedByDestination() {
            DimBranchEntity origin = branch(ANTIGUA_ID, "Antigua");
            when(dimBranchRepository.findById(ANTIGUA_ID)).thenReturn(Optional.of(origin));
            when(dimRouteRepository.findByOriginBranchId(ANTIGUA_ID)).thenReturn(List.of(
                    route(ROUTE_1, ANTIGUA_ID, ZACAPA_ID, false),
                    route(ROUTE_2, ANTIGUA_ID, AVILA_ID, null),
                    route(ROUTE_3, ANTIGUA_ID, AVILA_ID, true),
                    route(ROUTE_4, ANTIGUA_ID, UNKNOWN_ID, false),
                    route(ROUTE_5, ANTIGUA_ID, null, false)));
            when(dimBranchRepository.findAllById(any())).thenReturn(List.of(
                    branch(ZACAPA_ID, "Zacapa"), branch(AVILA_ID, "Ávila")));

            RouteMapResponse report = adminReportService.routeMap(ANTIGUA_ID);

            assertEquals(ANTIGUA_ID, report.getBranchId());
            assertEquals("Antigua", report.getBranchName());
            assertNull(report.getMessage());
            List<RouteMapResponse.RouteLine> routes = report.getRoutes();
            assertEquals(4, routes.size());
            assertEquals(ROUTE_2, routes.get(0).getRouteId());
            assertEquals(ROUTE_1, routes.get(1).getRouteId());
            RouteMapResponse.RouteLine first = routes.getFirst();
            assertEquals(ANTIGUA_ID, first.getOrigin().getBranchId());
            assertEquals(new BigDecimal("14.634900"), first.getOrigin().getLatitude());
            assertEquals(new BigDecimal("-90.506900"), first.getOrigin().getLongitude());
            assertEquals("Ávila", first.getDestination().getName());
            assertEquals(new BigDecimal("14.634900"), first.getDestination().getLatitude());
            assertEquals(new BigDecimal("120.50"), first.getDistanceKm());
            assertEquals(new BigDecimal("45.00"), first.getTicketPrice());
            RouteMapResponse.RouteLine missing = routes.get(2);
            assertEquals(UNKNOWN_ID, missing.getDestination().getBranchId());
            assertNull(missing.getDestination().getName());
            assertNull(missing.getDestination().getLatitude());
            assertNull(routes.get(3).getDestination().getBranchId());
            assertSame(first.getOrigin(), routes.get(3).getOrigin());
        }

        @Test
        void branchWithoutRoutes_returnsEmptyMapWithNotice() {
            when(dimBranchRepository.findById(ANTIGUA_ID)).thenReturn(Optional.of(branch(ANTIGUA_ID, "Antigua")));
            when(dimRouteRepository.findByOriginBranchId(ANTIGUA_ID)).thenReturn(List.of(
                    route(ROUTE_1, ANTIGUA_ID, ZACAPA_ID, true)));
            when(dimBranchRepository.findAllById(any())).thenReturn(List.of());

            RouteMapResponse report = adminReportService.routeMap(ANTIGUA_ID);

            assertTrue(report.getRoutes().isEmpty());
            assertEquals("La sucursal no tiene rutas registradas", report.getMessage());
        }

        @Test
        void unknownBranch_throwsNotFound() {
            when(dimBranchRepository.findById(ANTIGUA_ID)).thenReturn(Optional.empty());

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.BRANCH_NOT_FOUND,
                    () -> adminReportService.routeMap(ANTIGUA_ID));

            verifyNoInteractions(dimRouteRepository);
        }
    }

    private static List<DimBranchEntity> branches() {
        return List.of(
                branch(ZACAPA_ID, "Zacapa"),
                branch(AVILA_ID, "Ávila"),
                branch(ANTIGUA_ID, "Antigua"),
                branch(UNNAMED_ID, null));
    }

    private static DimBranchEntity branch(UUID id, String name) {
        return DimBranchEntity.builder()
                .branchId(id)
                .name(name)
                .latitude(new BigDecimal("14.634900"))
                .longitude(new BigDecimal("-90.506900"))
                .active(true)
                .build();
    }

    private static LedgerEntryEntity entry(UUID branchId, LedgerEntryType type, String amount) {
        return LedgerEntryEntity.builder()
                .id(UUID.randomUUID())
                .type(type)
                .concept(type == LedgerEntryType.INCOME ? LedgerConcept.TICKET_SALE : LedgerConcept.FUEL)
                .branchId(branchId)
                .amount(new BigDecimal(amount))
                .occurredOn(TODAY)
                .sourceType(type == LedgerEntryType.INCOME ? LedgerSourceType.TICKET_SALE : LedgerSourceType.TRIP_FUEL)
                .sourceId(UUID.randomUUID())
                .description("Venta")
                .build();
    }

    private static FactTicketSaleEntity sale(UUID routeId, UUID tripId, String price) {
        return FactTicketSaleEntity.builder()
                .ticketId(UUID.randomUUID())
                .routeId(routeId)
                .tripId(tripId)
                .pricePaid(price == null ? null : new BigDecimal(price))
                .paymentDate(TODAY)
                .build();
    }

    private static DimRouteEntity route(UUID routeId, UUID originId, UUID destinationId, Boolean deleted) {
        return DimRouteEntity.builder()
                .routeId(routeId)
                .originBranchId(originId)
                .destinationBranchId(destinationId)
                .distanceKm(new BigDecimal("120.50"))
                .ticketPrice(new BigDecimal("45.00"))
                .deleted(deleted)
                .build();
    }

    private static FactWorkshopLineEntity line(UUID categoryId, String categoryName, UUID busId, String amount) {
        return FactWorkshopLineEntity.builder()
                .lineId(UUID.randomUUID())
                .expenseId(UUID.randomUUID())
                .busId(busId)
                .branchId(ANTIGUA_ID)
                .serviceDate(TODAY)
                .categoryId(categoryId)
                .categoryName(categoryName)
                .type("LABOR")
                .description("Reparación")
                .amount(new BigDecimal(amount))
                .build();
    }

    private static void assertProfit(ProfitReportResponse.BranchProfit row, String income, String expense,
                                     String net) {
        assertEquals(new BigDecimal(income), row.getIncome());
        assertEquals(new BigDecimal(expense), row.getExpense());
        assertEquals(new BigDecimal(net), row.getNet());
    }

    private static void assertApiError(HttpStatus status, String code, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }
}
