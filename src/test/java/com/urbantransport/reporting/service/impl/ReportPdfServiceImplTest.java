package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.BusStatus;
import com.urbantransport.reporting.domain.DimBranchEntity;
import com.urbantransport.reporting.domain.DimBusEntity;
import com.urbantransport.reporting.domain.DimRouteEntity;
import com.urbantransport.reporting.domain.LedgerConcept;
import com.urbantransport.reporting.domain.LedgerEntryType;
import com.urbantransport.reporting.domain.LedgerSourceType;
import com.urbantransport.reporting.dto.BusDepreciationReportResponse;
import com.urbantransport.reporting.dto.BusListReportResponse;
import com.urbantransport.reporting.dto.DriverListReportResponse;
import com.urbantransport.reporting.dto.LedgerReportResponse;
import com.urbantransport.reporting.dto.ProfitReportResponse;
import com.urbantransport.reporting.dto.RentalReportResponse;
import com.urbantransport.reporting.dto.RouteMapResponse;
import com.urbantransport.reporting.dto.TicketSalesReportResponse;
import com.urbantransport.reporting.dto.TopRoutesReportResponse;
import com.urbantransport.reporting.dto.WorkshopBusReportResponse;
import com.urbantransport.reporting.dto.WorkshopCategoryReportResponse;
import com.urbantransport.reporting.pdf.PdfReportRenderer;
import com.urbantransport.reporting.pdf.ReportDocument;
import com.urbantransport.reporting.repository.DimBranchRepository;
import com.urbantransport.reporting.repository.DimBusRepository;
import com.urbantransport.reporting.repository.DimRouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportPdfServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final LocalDate TODAY = LocalDate.now(ZONE);
    private static final LocalDate FROM = TODAY.minusDays(30);
    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111112");
    private static final UUID BUS_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ROUTE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final byte[] PDF = {37, 80, 68, 70};

    @Mock
    private PdfReportRenderer pdfReportRenderer;

    @Mock
    private DimBranchRepository dimBranchRepository;

    @Mock
    private DimBusRepository dimBusRepository;

    @Mock
    private DimRouteRepository dimRouteRepository;

    @Captor
    private ArgumentCaptor<ReportDocument> documentCaptor;

    private ReportPdfServiceImpl reportPdfService;

    @BeforeEach
    void setUp() {
        reportPdfService = new ReportPdfServiceImpl(pdfReportRenderer, dimBranchRepository, dimBusRepository,
                dimRouteRepository);
        ReflectionTestUtils.setField(reportPdfService, "zone", ZONE);
        when(pdfReportRenderer.render(any())).thenReturn(PDF);
    }

    @Nested
    class Ledger {

        @Test
        void income_hasBranchSectionsWithSubtotalsAndGlobalTotal() {
            when(dimBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch("Xela")));
            LedgerReportResponse report = new LedgerReportResponse(LedgerEntryType.INCOME, List.of(
                    new LedgerReportResponse.BranchGroup(BRANCH_ID, "Xela", List.of(
                            entry(LedgerConcept.TICKET_SALE, "Venta en línea", "1234.5"),
                            entry(LedgerConcept.TICKET_SALE, "Cancelación", "-50")),
                            new BigDecimal("1184.50"))),
                    new BigDecimal("1184.50"));

            byte[] pdf = reportPdfService.income(report, FROM, TODAY, BRANCH_ID);

            assertSame(PDF, pdf);
            ReportDocument document = captured();
            assertEquals("Reporte de ingresos", document.title());
            assertFilters(document, "Desde", FROM.format(DATE), "Hasta", TODAY.format(DATE), "Sucursal", "Xela");
            ReportDocument.Section section = document.sections().getFirst();
            assertEquals("Xela", section.title());
            assertEquals(List.of("Fecha", "Concepto", "Referencia", "Monto"), headers(section));
            assertEquals(List.of(TODAY.format(DATE), "Boleto", "Venta en línea", "Q 1,234.50"), section.rows().get(0));
            assertEquals("Q -50.00", section.rows().get(1).get(3));
            assertEquals("Subtotal Xela", section.subtotalLabel());
            assertEquals("Q 1,184.50", section.subtotalValue());
            assertTotals(document, "Total global", "Q 1,184.50");
        }

        @Test
        void expenses_labelsEveryConceptAndUsesOpenFilters() {
            List<LedgerReportResponse.Entry> entries = Arrays.stream(LedgerConcept.values())
                    .map(concept -> entry(concept, null, "10"))
                    .toList();
            LedgerReportResponse report = new LedgerReportResponse(LedgerEntryType.EXPENSE, List.of(
                    new LedgerReportResponse.BranchGroup(null, "Sin sucursal asignada", entries,
                            new BigDecimal("70.00"))), new BigDecimal("70.00"));

            reportPdfService.expenses(report, null, null, null);

            ReportDocument document = captured();
            assertEquals("Reporte de gastos", document.title());
            assertFilters(document, "Desde", "Sin límite", "Hasta", "Sin límite", "Sucursal", "Todas");
            assertEquals(List.of("Boleto", "Alquiler", "Combustible", "Taller", "Repuestos", "Depreciación",
                    "Pago de chofer"), document.sections().getFirst().rows().stream().map(row -> row.get(1)).toList());
            assertEquals("-", document.sections().getFirst().rows().getFirst().get(2));
            verifyNoInteractions(dimBranchRepository);
        }

        @Test
        void unknownBranchFilter_showsItsId() {
            when(dimBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

            reportPdfService.income(new LedgerReportResponse(LedgerEntryType.INCOME, List.of(), BigDecimal.ZERO),
                    null, null, BRANCH_ID);

            ReportDocument document = captured();
            assertEquals(BRANCH_ID.toString(), document.filters().get(2).value());
            assertTrue(document.isEmpty());
        }
    }

    @Test
    void profit_hasOneRowPerBranchAndThreeTotals() {
        ProfitReportResponse report = new ProfitReportResponse(List.of(
                new ProfitReportResponse.BranchProfit(BRANCH_ID, "Xela", new BigDecimal("500"),
                        new BigDecimal("200"), new BigDecimal("300"))),
                new BigDecimal("500"), new BigDecimal("200"), new BigDecimal("300"));

        reportPdfService.profit(report, null, TODAY, null);

        ReportDocument document = captured();
        assertEquals("Reporte de ganancias", document.title());
        assertFilters(document, "Desde", "Sin límite", "Hasta", TODAY.format(DATE), "Sucursal", "Todas");
        ReportDocument.Section section = document.sections().getFirst();
        assertNull(section.title());
        assertEquals(List.of("Sucursal", "Ingresos", "Costos", "Ganancia neta"), headers(section));
        assertEquals(List.of("Xela", "Q 500.00", "Q 200.00", "Q 300.00"), section.rows().getFirst());
        assertTotals(document, "Total de ingresos", "Q 500.00", "Total de costos", "Q 200.00",
                "Ganancia neta global", "Q 300.00");
    }

    @Test
    void topRoutes_numbersRowsAndShowsPlaceholdersForMissingRoute() {
        TopRoutesReportResponse report = new TopRoutesReportResponse(List.of(
                new TopRoutesReportResponse.RouteDemand(ROUTE_ID, BRANCH_ID, "Xela", OTHER_BRANCH_ID, "Guatemala",
                        new BigDecimal("200.5"), new BigDecimal("75"), 4, 12, new BigDecimal("900")),
                new TopRoutesReportResponse.RouteDemand(UUID.randomUUID(), null, null, null, null, null, null, 1, 2,
                        new BigDecimal("100"))),
                14, new BigDecimal("1000"));

        reportPdfService.topRoutes(report, FROM, TODAY);

        ReportDocument document = captured();
        assertEquals("Rutas más demandadas", document.title());
        List<List<String>> rows = document.sections().getFirst().rows();
        assertEquals(List.of("1", "Xela", "Guatemala", "200.50", "Q 75.00", "4", "12", "Q 900.00"), rows.get(0));
        assertEquals(List.of("2", "-", "-", "-", "-", "1", "2", "Q 100.00"), rows.get(1));
        assertTotals(document, "Total de boletos vendidos", "14", "Ingreso total", "Q 1,000.00");
    }

    @Test
    void workshopByCategory_hasSectionPerCategoryWithLineTypesAndFilters() {
        when(dimBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch("Xela")));
        when(dimBusRepository.findById(BUS_ID)).thenReturn(Optional.of(DimBusEntity.builder().plate("P-111AAA")
                .build()));
        WorkshopCategoryReportResponse report = new WorkshopCategoryReportResponse(List.of(
                new WorkshopCategoryReportResponse.CategoryGroup(UUID.randomUUID(), "Motor", List.of(
                        workshopLine("LABOR", "P-111AAA"),
                        workshopLine("PART", null),
                        workshopLine("OTHER", "P-111AAA"),
                        workshopLine(null, "P-111AAA")),
                        new BigDecimal("40.00"))),
                new BigDecimal("40.00"));

        reportPdfService.workshopByCategory(report, FROM, TODAY, BRANCH_ID, BUS_ID);

        ReportDocument document = captured();
        assertEquals("Gastos de taller por categoría", document.title());
        assertEquals("Xela", document.filters().get(2).value());
        assertEquals("P-111AAA", document.filters().get(3).value());
        ReportDocument.Section section = document.sections().getFirst();
        assertEquals("Motor", section.title());
        assertEquals(List.of("Mano de obra", "Repuesto", "OTHER", "-"),
                section.rows().stream().map(row -> row.get(3)).toList());
        assertEquals("-", section.rows().get(1).get(0));
        assertEquals("Total Motor", section.subtotalLabel());
        assertTotals(document, "Gran total", "Q 40.00");
    }

    @Test
    void workshopByCategory_withUnknownOrNoBus_showsIdOrTodos() {
        when(dimBusRepository.findById(BUS_ID)).thenReturn(Optional.empty());
        WorkshopCategoryReportResponse report = new WorkshopCategoryReportResponse(List.of(), BigDecimal.ZERO);

        reportPdfService.workshopByCategory(report, null, null, null, BUS_ID);
        reportPdfService.workshopByCategory(report, null, null, null, null);

        verify(pdfReportRenderer, times(2)).render(documentCaptor.capture());
        assertEquals(BUS_ID.toString(), documentCaptor.getAllValues().get(0).filters().get(3).value());
        assertEquals("Todos", documentCaptor.getAllValues().get(1).filters().get(3).value());
    }

    @Nested
    class RouteMap {

        @Test
        void routes_showOriginAndDestinationCoordinates() {
            RouteMapResponse.MapPoint origin = new RouteMapResponse.MapPoint(BRANCH_ID, "Xela",
                    new BigDecimal("14.834700"), new BigDecimal("-91.518000"));
            RouteMapResponse report = new RouteMapResponse(BRANCH_ID, "Xela", List.of(
                    new RouteMapResponse.RouteLine(ROUTE_ID, origin, new RouteMapResponse.MapPoint(OTHER_BRANCH_ID,
                            "Guatemala", new BigDecimal("14.634900"), new BigDecimal("-90.506900")),
                            new BigDecimal("200.5"), new BigDecimal("75")),
                    new RouteMapResponse.RouteLine(UUID.randomUUID(), origin, new RouteMapResponse.MapPoint(null,
                            null, new BigDecimal("14.1"), null), null, null)),
                    null);

            reportPdfService.routeMap(report);

            ReportDocument document = captured();
            assertEquals("Mapa de rutas", document.title());
            assertFilters(document, "Sucursal de origen", "Xela", "Coordenadas de origen", "14.834700, -91.518000");
            List<List<String>> rows = document.sections().getFirst().rows();
            assertEquals(List.of("Guatemala", "14.634900, -90.506900", "200.50", "Q 75.00"), rows.get(0));
            assertEquals(List.of("-", "Sin coordenadas", "-", "-"), rows.get(1));
            assertTotals(document, "Total de rutas", "2");
        }

        @Test
        void branchWithoutRoutes_omitsOriginCoordinates() {
            reportPdfService.routeMap(new RouteMapResponse(BRANCH_ID, null, List.of(),
                    "La sucursal no tiene rutas registradas"));

            ReportDocument document = captured();
            assertFilters(document, "Sucursal de origen", "-");
            assertTrue(document.isEmpty());
        }
    }

    @Test
    void buses_showStatusFilterDriverPlaceholderAndTotals() {
        when(dimBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch("Xela")));
        BusListReportResponse report = new BusListReportResponse(List.of(
                new BusListReportResponse.BusRow(BUS_ID, "P-111AAA", "Volvo", "B8R", 40, "EN_TALLER", null, null,
                        new BigDecimal("1500"), 3),
                new BusListReportResponse.BusRow(UUID.randomUUID(), "P-222BBB", null, null, null, null,
                        UUID.randomUUID(), "Juan Pérez", null, 0)),
                3);

        reportPdfService.buses(report, BRANCH_ID, BusStatus.EN_TALLER);

        ReportDocument document = captured();
        assertEquals("Listado de buses", document.title());
        assertFilters(document, "Sucursal", "Xela", "Estado", "EN_TALLER");
        List<List<String>> rows = document.sections().getFirst().rows();
        assertEquals(List.of("P-111AAA", "Volvo", "B8R", "40", "EN_TALLER", "Sin asignar", "1,500.00", "3"),
                rows.get(0));
        assertEquals(List.of("P-222BBB", "-", "-", "-", "-", "Juan Pérez", "-", "0"), rows.get(1));
        assertTotals(document, "Total de buses", "2", "Total de viajes realizados", "3");
    }

    @Test
    void buses_withoutStatus_showsTodos() {
        when(dimBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch("Xela")));

        reportPdfService.buses(new BusListReportResponse(List.of(), 0), BRANCH_ID, null);

        assertEquals("Todos", captured().filters().get(1).value());
    }

    @Test
    void drivers_showActiveStateAndExpiration() {
        when(dimBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch("Xela")));
        LocalDate expiresOn = TODAY.plusMonths(3);
        DriverListReportResponse report = new DriverListReportResponse(List.of(
                new DriverListReportResponse.DriverRow(UUID.randomUUID(), "A-1", "Ana López", "A", expiresOn, true, 5),
                new DriverListReportResponse.DriverRow(UUID.randomUUID(), "B-2", "Luis Paz", "B", null, false, 0),
                new DriverListReportResponse.DriverRow(UUID.randomUUID(), null, null, null, null, null, 0)),
                5);

        reportPdfService.drivers(report, BRANCH_ID);

        ReportDocument document = captured();
        assertEquals("Listado de choferes", document.title());
        List<List<String>> rows = document.sections().getFirst().rows();
        assertEquals(List.of("A-1", "Ana López", "A", expiresOn.format(DATE), "Activo", "5"), rows.get(0));
        assertEquals(List.of("B-2", "Luis Paz", "B", "-", "Inactivo", "0"), rows.get(1));
        assertEquals("Inactivo", rows.get(2).get(4));
        assertTotals(document, "Total de choferes", "3", "Total de viajes realizados", "5");
    }

    @Nested
    class TicketSales {

        @Test
        void tripsShowRouteDepartureAndRouteFilterNames() {
            when(dimBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch("Xela")));
            when(dimBranchRepository.findById(OTHER_BRANCH_ID)).thenReturn(Optional.of(branch("Guatemala")));
            when(dimRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.of(DimRouteEntity.builder()
                    .originBranchId(BRANCH_ID).destinationBranchId(OTHER_BRANCH_ID).build()));
            when(dimBusRepository.findById(BUS_ID)).thenReturn(Optional.of(DimBusEntity.builder().plate("P-111AAA")
                    .build()));
            Instant departure = TODAY.atTime(8, 30).atZone(ZONE).toInstant();
            TicketSalesReportResponse report = new TicketSalesReportResponse(List.of(
                    new TicketSalesReportResponse.TripSales(UUID.randomUUID(), ROUTE_ID, "Xela", "Guatemala",
                            departure, BUS_ID, "P-111AAA", 2, new BigDecimal("100")),
                    new TicketSalesReportResponse.TripSales(UUID.randomUUID(), null, null, null, null, null, null, 1,
                            new BigDecimal("50"))),
                    3, new BigDecimal("150"));

            reportPdfService.ticketSales(report, BRANCH_ID, FROM, TODAY, ROUTE_ID, BUS_ID);

            ReportDocument document = captured();
            assertEquals("Ingresos por venta de boletos", document.title());
            assertFilters(document, "Desde", FROM.format(DATE), "Hasta", TODAY.format(DATE), "Sucursal", "Xela",
                    "Ruta", "Xela - Guatemala", "Bus", "P-111AAA");
            List<List<String>> rows = document.sections().getFirst().rows();
            assertEquals(List.of("Xela - Guatemala", TODAY.format(DATE) + " 08:30", "P-111AAA", "2", "Q 100.00"),
                    rows.get(0));
            assertEquals(List.of("- - -", "-", "-", "1", "Q 50.00"), rows.get(1));
            assertTotals(document, "Total de boletos", "3", "Ingreso total", "Q 150.00");
        }

        @Test
        void unknownOrMissingRoute_showsIdOrTodas() {
            when(dimBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch("Xela")));
            when(dimRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.empty());
            TicketSalesReportResponse report = new TicketSalesReportResponse(List.of(), 0, BigDecimal.ZERO);

            reportPdfService.ticketSales(report, BRANCH_ID, null, null, ROUTE_ID, null);
            reportPdfService.ticketSales(report, BRANCH_ID, null, null, null, null);

            verify(pdfReportRenderer, times(2)).render(documentCaptor.capture());
            assertEquals(ROUTE_ID.toString(), documentCaptor.getAllValues().get(0).filters().get(3).value());
            assertEquals("Todas", documentCaptor.getAllValues().get(1).filters().get(3).value());
        }
    }

    @Test
    void rentals_showCustomerDatesAndBusPlaceholder() {
        when(dimBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch("Xela")));
        RentalReportResponse report = new RentalReportResponse(List.of(
                new RentalReportResponse.RentalRow(UUID.randomUUID(), "COUNTER", "CF", "Xela", "Tikal",
                        TODAY.plusDays(3), TODAY.plusDays(4), TODAY, BUS_ID, "P-111AAA", new BigDecimal("1000")),
                new RentalReportResponse.RentalRow(UUID.randomUUID(), "ONLINE", "123", "Xela", "Cantel",
                        TODAY.plusDays(5), null, TODAY, null, null, new BigDecimal("250.5"))),
                new BigDecimal("1250.5"));

        reportPdfService.rentals(report, BRANCH_ID, FROM, TODAY);

        ReportDocument document = captured();
        assertEquals("Ingresos por alquiler de buses", document.title());
        List<List<String>> rows = document.sections().getFirst().rows();
        assertEquals(List.of("CF", "Xela", "Tikal", TODAY.plusDays(3).format(DATE), TODAY.plusDays(4).format(DATE),
                "P-111AAA", "Q 1,000.00"), rows.get(0));
        assertEquals("-", rows.get(1).get(4));
        assertEquals("Sin asignar", rows.get(1).get(5));
        assertTotals(document, "Total", "Q 1,250.50");
    }

    @Nested
    class BusDepreciation {

        @Test
        void configuredRate_isShownAsFilterAndPerBus() {
            when(dimBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch("Xela")));
            Instant effectiveFrom = TODAY.minusDays(10).atTime(9, 15).atZone(ZONE).toInstant();
            BusDepreciationReportResponse report = new BusDepreciationReportResponse(new BigDecimal("0.75"),
                    effectiveFrom, List.of(new BusDepreciationReportResponse.BusDepreciation(BUS_ID, "P-111AAA",
                    new BigDecimal("120.5"), new BigDecimal("0.75"), new BigDecimal("90.38"))),
                    new BigDecimal("120.5"), new BigDecimal("90.38"));

            reportPdfService.busDepreciation(report, BRANCH_ID, FROM, TODAY);

            ReportDocument document = captured();
            assertEquals("Depreciación por bus", document.title());
            assertEquals("Q 0.7500 por km desde " + TODAY.minusDays(10).format(DATE) + " 09:15",
                    document.filters().get(3).value());
            assertEquals(List.of("P-111AAA", "120.50", "Q 0.7500", "Q 90.38"),
                    document.sections().getFirst().rows().getFirst());
            assertTotals(document, "Total de km", "120.50", "Depreciación total", "Q 90.38");
        }

        @Test
        void withoutRate_showsNoConfiguredRate() {
            when(dimBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch("Xela")));
            BusDepreciationReportResponse report = new BusDepreciationReportResponse(null, null, List.of(
                    new BusDepreciationReportResponse.BusDepreciation(BUS_ID, "P-111AAA", BigDecimal.ZERO, null,
                            BigDecimal.ZERO)), BigDecimal.ZERO, BigDecimal.ZERO);

            reportPdfService.busDepreciation(report, BRANCH_ID, null, null);

            ReportDocument document = captured();
            assertEquals("Sin tasa configurada", document.filters().get(3).value());
            assertEquals("-", document.sections().getFirst().rows().getFirst().get(2));
        }
    }

    @Test
    void workshopByBus_hasSectionPerBusWithCategoryTotals() {
        when(dimBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch("Xela")));
        WorkshopBusReportResponse report = new WorkshopBusReportResponse(List.of(
                new WorkshopBusReportResponse.BusGroup(BUS_ID, "P-111AAA", List.of(
                        new WorkshopBusReportResponse.CategoryTotal(UUID.randomUUID(), "Motor", 2,
                                new BigDecimal("400"))), new BigDecimal("400")),
                new WorkshopBusReportResponse.BusGroup(null, null, List.of(), new BigDecimal("0"))),
                new BigDecimal("400"));

        reportPdfService.workshopByBus(report, BRANCH_ID, FROM, TODAY);

        ReportDocument document = captured();
        assertEquals("Gastos de taller por bus y categoría", document.title());
        assertEquals("Bus P-111AAA", document.sections().get(0).title());
        assertEquals(List.of("Motor", "2", "Q 400.00"), document.sections().get(0).rows().getFirst());
        assertEquals("Total del bus", document.sections().get(0).subtotalLabel());
        assertEquals("Q 400.00", document.sections().get(0).subtotalValue());
        assertEquals("Bus -", document.sections().get(1).title());
        assertTotals(document, "Gran total", "Q 400.00");
    }

    private ReportDocument captured() {
        verify(pdfReportRenderer).render(documentCaptor.capture());
        return documentCaptor.getValue();
    }

    private static DimBranchEntity branch(String name) {
        return DimBranchEntity.builder().branchId(BRANCH_ID).name(name).build();
    }

    private static LedgerReportResponse.Entry entry(LedgerConcept concept, String description, String amount) {
        return new LedgerReportResponse.Entry(UUID.randomUUID(), TODAY, concept, LedgerSourceType.TICKET_SALE,
                UUID.randomUUID(), description, new BigDecimal(amount));
    }

    private static WorkshopCategoryReportResponse.Line workshopLine(String type, String plate) {
        return new WorkshopCategoryReportResponse.Line(UUID.randomUUID(), UUID.randomUUID(), BUS_ID, plate, TODAY,
                "Ajuste", type, new BigDecimal("10"));
    }

    private static List<String> headers(ReportDocument.Section section) {
        return section.columns().stream().map(ReportDocument.Column::header).toList();
    }

    private static void assertFilters(ReportDocument document, String... labelsAndValues) {
        assertEquals(labelsAndValues.length / 2, document.filters().size());
        for (int i = 0; i < labelsAndValues.length; i += 2) {
            assertEquals(labelsAndValues[i], document.filters().get(i / 2).label());
            assertEquals(labelsAndValues[i + 1], document.filters().get(i / 2).value());
        }
    }

    private static void assertTotals(ReportDocument document, String... labelsAndValues) {
        assertEquals(labelsAndValues.length / 2, document.totals().size());
        for (int i = 0; i < labelsAndValues.length; i += 2) {
            assertEquals(labelsAndValues[i], document.totals().get(i / 2).label());
            assertEquals(labelsAndValues[i + 1], document.totals().get(i / 2).value());
        }
    }
}
