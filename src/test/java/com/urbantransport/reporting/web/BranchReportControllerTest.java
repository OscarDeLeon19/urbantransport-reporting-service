package com.urbantransport.reporting.web;

import com.urbantransport.reporting.config.SecurityConfig;
import com.urbantransport.reporting.domain.BusStatus;
import com.urbantransport.reporting.dto.BusDepreciationReportResponse;
import com.urbantransport.reporting.dto.BusListReportResponse;
import com.urbantransport.reporting.dto.DriverListReportResponse;
import com.urbantransport.reporting.dto.RentalReportResponse;
import com.urbantransport.reporting.dto.TicketSalesReportResponse;
import com.urbantransport.reporting.dto.WorkshopBusReportResponse;
import com.urbantransport.reporting.exception.ErrorCodes;
import com.urbantransport.reporting.service.BranchReportService;
import com.urbantransport.reporting.service.ReportPdfService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BranchReportController.class)
@Import(SecurityConfig.class)
class BranchReportControllerTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Guatemala"));
    private static final LocalDate FROM = TODAY.minusDays(30);
    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111112");
    private static final UUID BUS_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ROUTE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final byte[] PDF = {37, 80, 68, 70, 45};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BranchReportService branchReportService;

    @MockitoBean
    private ReportPdfService reportPdfService;

    @Test
    void buses_forceTokenBranchAndPassStatus() throws Exception {
        when(branchReportService.buses(BRANCH_ID, BusStatus.EN_TALLER)).thenReturn(buses());

        mockMvc.perform(get("/reports/buses").with(branchAdmin(BRANCH_ID))
                        .param("status", "EN_TALLER")
                        .param("branchId", OTHER_BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buses[0].plate").value("P-111AAA"))
                .andExpect(jsonPath("$.buses[0].currentDriverName").value("Juan Perez"))
                .andExpect(jsonPath("$.totalTrips").value(3));

        verifyNoInteractions(reportPdfService);
    }

    @Test
    void buses_asPdf_downloadsAttachment() throws Exception {
        BusListReportResponse report = buses();
        when(branchReportService.buses(BRANCH_ID, null)).thenReturn(report);
        when(reportPdfService.buses(report, BRANCH_ID, null)).thenReturn(PDF);

        mockMvc.perform(get("/reports/buses").with(branchAdmin(BRANCH_ID)).param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"listado-de-buses.pdf\""))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void buses_withUnknownStatus_returnsValidationError() throws Exception {
        mockMvc.perform(get("/reports/buses").with(branchAdmin(BRANCH_ID)).param("status", "VOLANDO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));

        verifyNoInteractions(branchReportService);
    }

    @Test
    void invalidFormat_returnsBadRequestWithoutComputingReport() throws Exception {
        mockMvc.perform(get("/reports/drivers").with(branchAdmin(BRANCH_ID)).param("format", "csv"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_FORMAT));

        verifyNoInteractions(branchReportService, reportPdfService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/reports/buses", "/reports/drivers", "/reports/ticket-sales", "/reports/rentals",
            "/reports/bus-depreciation", "/reports/workshop-by-bus"})
    void systemAdmin_isDenied(String path) throws Exception {
        mockMvc.perform(get(path).with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(branchReportService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/reports/buses", "/reports/drivers", "/reports/ticket-sales", "/reports/rentals",
            "/reports/bus-depreciation", "/reports/workshop-by-bus"})
    void branchAdminWithoutBranch_returnsBranchRequired(String path) throws Exception {
        mockMvc.perform(get(path).with(branchAdmin(null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_REQUIRED));

        verifyNoInteractions(branchReportService);
    }

    @Test
    void withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/reports/buses"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(branchReportService);
    }

    @Test
    void drivers_asJsonAndPdf() throws Exception {
        DriverListReportResponse report = new DriverListReportResponse(List.of(new DriverListReportResponse.DriverRow(
                UUID.randomUUID(), "A-1", "Ana Lopez", "A", TODAY.plusMonths(6), true, 5)), 5);
        when(branchReportService.drivers(BRANCH_ID)).thenReturn(report);
        when(reportPdfService.drivers(report, BRANCH_ID)).thenReturn(PDF);

        mockMvc.perform(get("/reports/drivers").with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drivers[0].licenseNumber").value("A-1"))
                .andExpect(jsonPath("$.drivers[0].licenseExpiresOn").value(TODAY.plusMonths(6).toString()));
        mockMvc.perform(get("/reports/drivers").with(branchAdmin(BRANCH_ID)).param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"listado-de-choferes.pdf\""));
    }

    @Test
    void ticketSales_passFiltersAsJsonAndPdf() throws Exception {
        TicketSalesReportResponse report = new TicketSalesReportResponse(List.of(
                new TicketSalesReportResponse.TripSales(UUID.randomUUID(), ROUTE_ID, "Xela", "Guatemala", null,
                        BUS_ID, "P-111AAA", 2, new BigDecimal("100.00"))), 2, new BigDecimal("100.00"));
        when(branchReportService.ticketSales(BRANCH_ID, FROM, TODAY, ROUTE_ID, BUS_ID)).thenReturn(report);
        when(reportPdfService.ticketSales(report, BRANCH_ID, FROM, TODAY, ROUTE_ID, BUS_ID)).thenReturn(PDF);

        mockMvc.perform(get("/reports/ticket-sales").with(branchAdmin(BRANCH_ID))
                        .param("from", FROM.toString()).param("to", TODAY.toString())
                        .param("routeId", ROUTE_ID.toString()).param("busId", BUS_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trips[0].ticketsSold").value(2))
                .andExpect(jsonPath("$.totalIncome").value(100.0));
        mockMvc.perform(get("/reports/ticket-sales").with(branchAdmin(BRANCH_ID))
                        .param("from", FROM.toString()).param("to", TODAY.toString())
                        .param("routeId", ROUTE_ID.toString()).param("busId", BUS_ID.toString())
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"ingresos-por-boletos.pdf\""));
    }

    @Test
    void rentals_asJsonAndPdf() throws Exception {
        RentalReportResponse report = new RentalReportResponse(List.of(new RentalReportResponse.RentalRow(
                UUID.randomUUID(), "COUNTER", "CF", "Xela", "Tikal", TODAY, null, TODAY, null, null,
                new BigDecimal("1000.00"))), new BigDecimal("1000.00"));
        when(branchReportService.rentals(BRANCH_ID, null, TODAY)).thenReturn(report);
        when(reportPdfService.rentals(report, BRANCH_ID, null, TODAY)).thenReturn(PDF);

        mockMvc.perform(get("/reports/rentals").with(branchAdmin(BRANCH_ID)).param("to", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentals[0].customerNit").value("CF"))
                .andExpect(jsonPath("$.total").value(1000.0));
        mockMvc.perform(get("/reports/rentals").with(branchAdmin(BRANCH_ID)).param("to", TODAY.toString())
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"ingresos-por-alquiler.pdf\""));
    }

    @Test
    void busDepreciation_asJsonAndPdf() throws Exception {
        BusDepreciationReportResponse report = new BusDepreciationReportResponse(new BigDecimal("0.7500"), null,
                List.of(new BusDepreciationReportResponse.BusDepreciation(BUS_ID, "P-111AAA",
                        new BigDecimal("120.50"), new BigDecimal("0.7500"), new BigDecimal("90.38"))),
                new BigDecimal("120.50"), new BigDecimal("90.38"));
        when(branchReportService.busDepreciation(BRANCH_ID, FROM, null)).thenReturn(report);
        when(reportPdfService.busDepreciation(report, BRANCH_ID, FROM, null)).thenReturn(PDF);

        mockMvc.perform(get("/reports/bus-depreciation").with(branchAdmin(BRANCH_ID))
                        .param("from", FROM.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountPerKm").value(0.75))
                .andExpect(jsonPath("$.buses[0].accumulated").value(90.38));
        mockMvc.perform(get("/reports/bus-depreciation").with(branchAdmin(BRANCH_ID))
                        .param("from", FROM.toString()).param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"depreciacion-por-bus.pdf\""));
    }

    @Test
    void workshopByBus_asJsonAndPdf() throws Exception {
        WorkshopBusReportResponse report = new WorkshopBusReportResponse(List.of(new WorkshopBusReportResponse.BusGroup(
                BUS_ID, "P-111AAA", List.of(new WorkshopBusReportResponse.CategoryTotal(UUID.randomUUID(), "Motor",
                3, new BigDecimal("900.00"))), new BigDecimal("900.00"))), new BigDecimal("900.00"));
        when(branchReportService.workshopByBus(BRANCH_ID, null, null)).thenReturn(report);
        when(reportPdfService.workshopByBus(report, BRANCH_ID, null, null)).thenReturn(PDF);

        mockMvc.perform(get("/reports/workshop-by-bus").with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buses[0].categories[0].lines").value(3))
                .andExpect(jsonPath("$.total").value(900.0));
        mockMvc.perform(get("/reports/workshop-by-bus").with(branchAdmin(BRANCH_ID)).param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"taller-por-bus.pdf\""));
    }

    private static BusListReportResponse buses() {
        return new BusListReportResponse(List.of(new BusListReportResponse.BusRow(BUS_ID, "P-111AAA", "Volvo", "B8R",
                40, "EN_TALLER", UUID.randomUUID(), "Juan Perez", new BigDecimal("1500.00"), 3)), 3);
    }

    private static RequestPostProcessor branchAdmin(UUID branchId) {
        return user("BRANCH_ADMIN", branchId);
    }

    private static RequestPostProcessor user(String role, UUID branchId) {
        return jwt()
                .jwt(token -> {
                    token.subject("99999999-9999-9999-9999-999999999999").claim("roles", List.of(role));
                    if (branchId != null) {
                        token.claim("branchId", branchId.toString());
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
