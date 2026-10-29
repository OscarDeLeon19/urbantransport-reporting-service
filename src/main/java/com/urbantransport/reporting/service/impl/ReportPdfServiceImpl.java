package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.BusStatus;
import com.urbantransport.reporting.domain.DimBranchEntity;
import com.urbantransport.reporting.domain.DimBusEntity;
import com.urbantransport.reporting.domain.DimRouteEntity;
import com.urbantransport.reporting.domain.LedgerConcept;
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
import com.urbantransport.reporting.pdf.ReportDocument.Column;
import com.urbantransport.reporting.pdf.ReportDocument.Filter;
import com.urbantransport.reporting.pdf.ReportDocument.Section;
import com.urbantransport.reporting.pdf.ReportDocument.Total;
import com.urbantransport.reporting.repository.DimBranchRepository;
import com.urbantransport.reporting.repository.DimBusRepository;
import com.urbantransport.reporting.repository.DimRouteRepository;
import com.urbantransport.reporting.service.ReportPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportPdfServiceImpl implements ReportPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String EMPTY = "-";

    private final PdfReportRenderer pdfReportRenderer;
    private final DimBranchRepository dimBranchRepository;
    private final DimBusRepository dimBusRepository;
    private final DimRouteRepository dimRouteRepository;

    @Value("${app.zone:America/Guatemala}")
    private ZoneId zone;

    @Override
    @Transactional(readOnly = true)
    public byte[] income(LedgerReportResponse report, LocalDate from, LocalDate to, UUID branchId) {
        return ledger("Reporte de ingresos", report, from, to, branchId);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] expenses(LedgerReportResponse report, LocalDate from, LocalDate to, UUID branchId) {
        return ledger("Reporte de gastos", report, from, to, branchId);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] profit(ProfitReportResponse report, LocalDate from, LocalDate to, UUID branchId) {
        List<Filter> filters = dateFilters(from, to);
        filters.add(new Filter("Sucursal", branchFilter(branchId)));

        List<List<String>> rows = report.getBranches().stream()
                .map(branch -> List.of(branch.getBranchName(), money(branch.getIncome()),
                        money(branch.getExpense()), money(branch.getNet())))
                .toList();
        Section section = Section.of(null, List.of(
                Column.text("Sucursal", 4),
                Column.number("Ingresos", 2),
                Column.number("Costos", 2),
                Column.number("Ganancia neta", 2)), rows);

        return pdfReportRenderer.render(new ReportDocument("Reporte de ganancias", filters, List.of(section), List.of(
                new Total("Total de ingresos", money(report.getTotalIncome())),
                new Total("Total de costos", money(report.getTotalExpense())),
                new Total("Ganancia neta global", money(report.getTotalNet())))));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] topRoutes(TopRoutesReportResponse report, LocalDate from, LocalDate to) {
        List<List<String>> rows = new ArrayList<>();
        int position = 1;
        for (TopRoutesReportResponse.RouteDemand route : report.getRoutes()) {
            rows.add(List.of(
                    String.valueOf(position++),
                    text(route.getOriginName()),
                    text(route.getDestinationName()),
                    decimal(route.getDistanceKm()),
                    money(route.getTicketPrice()),
                    String.valueOf(route.getTrips()),
                    String.valueOf(route.getTicketsSold()),
                    money(route.getIncome())));
        }
        Section section = Section.of(null, List.of(
                Column.number("#", 0.6f),
                Column.text("Origen", 3),
                Column.text("Destino", 3),
                Column.number("Distancia (km)", 1.6f),
                Column.number("Precio", 1.5f),
                Column.number("Viajes", 1.2f),
                Column.number("Boletos", 1.2f),
                Column.number("Ingreso", 1.8f)), rows);

        return pdfReportRenderer.render(new ReportDocument("Rutas más demandadas", dateFilters(from, to),
                List.of(section), List.of(
                new Total("Total de boletos vendidos", String.valueOf(report.getTotalTickets())),
                new Total("Ingreso total", money(report.getTotalIncome())))));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] workshopByCategory(WorkshopCategoryReportResponse report, LocalDate from, LocalDate to,
                                     UUID branchId, UUID busId) {
        List<Filter> filters = dateFilters(from, to);
        filters.add(new Filter("Sucursal", branchFilter(branchId)));
        filters.add(new Filter("Bus", busFilter(busId)));

        List<Column> columns = List.of(
                Column.text("Bus", 1.5f),
                Column.text("Fecha", 1.3f),
                Column.text("Descripción", 5),
                Column.text("Tipo", 1.5f),
                Column.number("Monto", 1.7f));
        List<Section> sections = report.getCategories().stream()
                .map(category -> new Section(
                        text(category.getCategoryName()),
                        columns,
                        category.getLines().stream()
                                .map(line -> List.of(text(line.getPlate()), date(line.getServiceDate()),
                                        text(line.getDescription()), lineType(line.getType()),
                                        money(line.getAmount())))
                                .toList(),
                        "Total " + text(category.getCategoryName()),
                        money(category.getSubtotal())))
                .toList();

        return pdfReportRenderer.render(new ReportDocument("Gastos de taller por categoría", filters, sections,
                List.of(new Total("Gran total", money(report.getTotal())))));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] routeMap(RouteMapResponse report) {
        List<Filter> filters = new ArrayList<>();
        filters.add(new Filter("Sucursal de origen", text(report.getBranchName())));
        if (!report.getRoutes().isEmpty()) {
            RouteMapResponse.MapPoint origin = report.getRoutes().getFirst().getOrigin();
            filters.add(new Filter("Coordenadas de origen", coordinates(origin)));
        }

        List<List<String>> rows = report.getRoutes().stream()
                .map(route -> List.of(
                        text(route.getDestination().getName()),
                        coordinates(route.getDestination()),
                        decimal(route.getDistanceKm()),
                        money(route.getTicketPrice())))
                .toList();
        Section section = Section.of(null, List.of(
                Column.text("Destino", 4),
                Column.text("Coordenadas de destino", 4),
                Column.number("Distancia (km)", 2),
                Column.number("Precio", 2)), rows);

        return pdfReportRenderer.render(new ReportDocument("Mapa de rutas", filters, List.of(section),
                List.of(new Total("Total de rutas", String.valueOf(report.getRoutes().size())))));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] buses(BusListReportResponse report, UUID branchId, BusStatus status) {
        List<Filter> filters = new ArrayList<>();
        filters.add(new Filter("Sucursal", branchFilter(branchId)));
        filters.add(new Filter("Estado", status == null ? "Todos" : status.name()));

        List<List<String>> rows = report.getBuses().stream()
                .map(bus -> List.of(
                        text(bus.getPlate()),
                        text(bus.getBrand()),
                        text(bus.getModel()),
                        bus.getCapacity() == null ? EMPTY : String.valueOf(bus.getCapacity()),
                        text(bus.getStatus()),
                        bus.getCurrentDriverName() == null ? "Sin asignar" : bus.getCurrentDriverName(),
                        decimal(bus.getOdometerKm()),
                        String.valueOf(bus.getCompletedTrips())))
                .toList();
        Section section = Section.of(null, List.of(
                Column.text("Placa", 1.4f),
                Column.text("Marca", 1.8f),
                Column.text("Modelo", 1.8f),
                Column.number("Capacidad", 1.2f),
                Column.text("Estado", 1.5f),
                Column.text("Chofer actual", 3),
                Column.number("Kilometraje", 1.6f),
                Column.number("Viajes realizados", 1.5f)), rows);

        return pdfReportRenderer.render(new ReportDocument("Listado de buses", filters, List.of(section), List.of(
                new Total("Total de buses", String.valueOf(report.getBuses().size())),
                new Total("Total de viajes realizados", String.valueOf(report.getTotalTrips())))));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] drivers(DriverListReportResponse report, UUID branchId) {
        List<Filter> filters = new ArrayList<>();
        filters.add(new Filter("Sucursal", branchFilter(branchId)));

        List<List<String>> rows = report.getDrivers().stream()
                .map(driver -> List.of(
                        text(driver.getLicenseNumber()),
                        text(driver.getFullName()),
                        text(driver.getLicenseType()),
                        date(driver.getLicenseExpiresOn()),
                        Boolean.TRUE.equals(driver.getActive()) ? "Activo" : "Inactivo",
                        String.valueOf(driver.getCompletedTrips())))
                .toList();
        Section section = Section.of(null, List.of(
                Column.text("Licencia", 2),
                Column.text("Nombre", 4),
                Column.text("Tipo", 1),
                Column.text("Vencimiento", 1.6f),
                Column.text("Estado", 1.3f),
                Column.number("Viajes realizados", 1.6f)), rows);

        return pdfReportRenderer.render(new ReportDocument("Listado de choferes", filters, List.of(section), List.of(
                new Total("Total de choferes", String.valueOf(report.getDrivers().size())),
                new Total("Total de viajes realizados", String.valueOf(report.getTotalTrips())))));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] ticketSales(TicketSalesReportResponse report, UUID branchId, LocalDate from, LocalDate to,
                              UUID routeId, UUID busId) {
        List<Filter> filters = dateFilters(from, to);
        filters.add(new Filter("Sucursal", branchFilter(branchId)));
        filters.add(new Filter("Ruta", routeFilter(routeId)));
        filters.add(new Filter("Bus", busFilter(busId)));

        List<List<String>> rows = report.getTrips().stream()
                .map(trip -> List.of(
                        text(trip.getOriginName()) + " - " + text(trip.getDestinationName()),
                        dateTime(trip.getDepartureAt()),
                        text(trip.getPlate()),
                        String.valueOf(trip.getTicketsSold()),
                        money(trip.getIncome())))
                .toList();
        Section section = Section.of(null, List.of(
                Column.text("Ruta", 5),
                Column.text("Fecha de salida", 2),
                Column.text("Bus", 1.5f),
                Column.number("Boletos", 1.2f),
                Column.number("Ingreso", 1.8f)), rows);

        return pdfReportRenderer.render(new ReportDocument("Ingresos por venta de boletos", filters,
                List.of(section), List.of(
                new Total("Total de boletos", String.valueOf(report.getTotalTickets())),
                new Total("Ingreso total", money(report.getTotalIncome())))));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] rentals(RentalReportResponse report, UUID branchId, LocalDate from, LocalDate to) {
        List<Filter> filters = dateFilters(from, to);
        filters.add(new Filter("Sucursal", branchFilter(branchId)));

        List<List<String>> rows = report.getRentals().stream()
                .map(rental -> List.of(
                        text(rental.getCustomerNit()),
                        text(rental.getOriginText()),
                        text(rental.getDestinationText()),
                        date(rental.getDepartureDate()),
                        date(rental.getReturnDate()),
                        rental.getPlate() == null ? "Sin asignar" : rental.getPlate(),
                        money(rental.getAmount())))
                .toList();
        Section section = Section.of(null, List.of(
                Column.text("Cliente (NIT)", 1.6f),
                Column.text("Origen", 3),
                Column.text("Destino", 3),
                Column.text("Salida", 1.3f),
                Column.text("Regreso", 1.3f),
                Column.text("Bus", 1.4f),
                Column.number("Precio", 1.6f)), rows);

        return pdfReportRenderer.render(new ReportDocument("Ingresos por alquiler de buses", filters,
                List.of(section), List.of(new Total("Total", money(report.getTotal())))));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] busDepreciation(BusDepreciationReportResponse report, UUID branchId, LocalDate from,
                                  LocalDate to) {
        List<Filter> filters = dateFilters(from, to);
        filters.add(new Filter("Sucursal", branchFilter(branchId)));
        filters.add(new Filter("Tasa vigente", report.getAmountPerKm() == null ? "Sin tasa configurada"
                : rate(report.getAmountPerKm()) + " por km desde " + dateTime(report.getRateEffectiveFrom())));

        List<List<String>> rows = report.getBuses().stream()
                .map(bus -> List.of(
                        text(bus.getPlate()),
                        decimal(bus.getKm()),
                        rate(bus.getAmountPerKm()),
                        money(bus.getAccumulated())))
                .toList();
        Section section = Section.of(null, List.of(
                Column.text("Placa", 2),
                Column.number("Km recorridos", 2),
                Column.number("Monto por km", 2),
                Column.number("Depreciación acumulada", 2.5f)), rows);

        return pdfReportRenderer.render(new ReportDocument("Depreciación por bus", filters, List.of(section),
                List.of(
                        new Total("Total de km", decimal(report.getTotalKm())),
                        new Total("Depreciación total", money(report.getTotalDepreciation())))));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] workshopByBus(WorkshopBusReportResponse report, UUID branchId, LocalDate from, LocalDate to) {
        List<Filter> filters = dateFilters(from, to);
        filters.add(new Filter("Sucursal", branchFilter(branchId)));

        List<Column> columns = List.of(
                Column.text("Categoría", 5),
                Column.number("Líneas", 1.2f),
                Column.number("Monto", 2));
        List<Section> sections = report.getBuses().stream()
                .map(bus -> new Section(
                        "Bus " + text(bus.getPlate()),
                        columns,
                        bus.getCategories().stream()
                                .map(category -> List.of(text(category.getCategoryName()),
                                        String.valueOf(category.getLines()), money(category.getAmount())))
                                .toList(),
                        "Total del bus",
                        money(bus.getTotal())))
                .toList();

        return pdfReportRenderer.render(new ReportDocument("Gastos de taller por bus y categoría", filters, sections,
                List.of(new Total("Gran total", money(report.getTotal())))));
    }

    private byte[] ledger(String title, LedgerReportResponse report, LocalDate from, LocalDate to, UUID branchId) {
        List<Filter> filters = dateFilters(from, to);
        filters.add(new Filter("Sucursal", branchFilter(branchId)));

        List<Column> columns = List.of(
                Column.text("Fecha", 1.3f),
                Column.text("Concepto", 1.8f),
                Column.text("Referencia", 6),
                Column.number("Monto", 1.8f));
        List<Section> sections = report.getBranches().stream()
                .map(branch -> new Section(
                        branch.getBranchName(),
                        columns,
                        branch.getEntries().stream()
                                .map(entry -> List.of(date(entry.getDate()), conceptLabel(entry.getConcept()),
                                        text(entry.getDescription()), money(entry.getAmount())))
                                .toList(),
                        "Subtotal " + branch.getBranchName(),
                        money(branch.getSubtotal())))
                .toList();

        return pdfReportRenderer.render(new ReportDocument(title, filters, sections,
                List.of(new Total("Total global", money(report.getTotal())))));
    }

    private List<Filter> dateFilters(LocalDate from, LocalDate to) {
        List<Filter> filters = new ArrayList<>();
        filters.add(new Filter("Desde", from == null ? "Sin límite" : date(from)));
        filters.add(new Filter("Hasta", to == null ? "Sin límite" : date(to)));
        return filters;
    }

    private String branchFilter(UUID branchId) {
        if (branchId == null) {
            return "Todas";
        }
        return dimBranchRepository.findById(branchId)
                .map(DimBranchEntity::getName)
                .orElse(branchId.toString());
    }

    private String busFilter(UUID busId) {
        if (busId == null) {
            return "Todos";
        }
        return dimBusRepository.findById(busId)
                .map(DimBusEntity::getPlate)
                .orElse(busId.toString());
    }

    private String routeFilter(UUID routeId) {
        if (routeId == null) {
            return "Todas";
        }
        DimRouteEntity route = dimRouteRepository.findById(routeId).orElse(null);
        if (route == null) {
            return routeId.toString();
        }
        return branchFilter(route.getOriginBranchId()) + " - " + branchFilter(route.getDestinationBranchId());
    }

    private String conceptLabel(LedgerConcept concept) {
        return switch (concept) {
            case TICKET_SALE -> "Boleto";
            case RENTAL -> "Alquiler";
            case FUEL -> "Combustible";
            case WORKSHOP -> "Taller";
            case SPARE_PARTS -> "Repuestos";
            case DEPRECIATION -> "Depreciación";
            case DRIVER_PAY -> "Pago de chofer";
        };
    }

    private String lineType(String type) {
        if ("LABOR".equals(type)) {
            return "Mano de obra";
        }
        if ("PART".equals(type)) {
            return "Repuesto";
        }
        return text(type);
    }

    private String coordinates(RouteMapResponse.MapPoint point) {
        if (point.getLatitude() == null || point.getLongitude() == null) {
            return "Sin coordenadas";
        }
        return point.getLatitude().toPlainString() + ", " + point.getLongitude().toPlainString();
    }

    private String money(BigDecimal value) {
        return value == null ? EMPTY : "Q " + String.format(Locale.US, "%,.2f", value);
    }

    private String decimal(BigDecimal value) {
        return value == null ? EMPTY : String.format(Locale.US, "%,.2f", value);
    }

    private String rate(BigDecimal value) {
        return value == null ? EMPTY : "Q " + String.format(Locale.US, "%,.4f", value);
    }

    private String date(LocalDate value) {
        return value == null ? EMPTY : value.format(DATE);
    }

    private String dateTime(Instant value) {
        return value == null ? EMPTY : value.atZone(zone).format(DATE_TIME);
    }

    private String text(String value) {
        return value == null ? EMPTY : value;
    }
}
