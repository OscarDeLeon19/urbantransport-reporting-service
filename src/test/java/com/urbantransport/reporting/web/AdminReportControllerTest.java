package com.urbantransport.reporting.web;

import com.urbantransport.reporting.config.SecurityConfig;
import com.urbantransport.reporting.domain.LedgerConcept;
import com.urbantransport.reporting.domain.LedgerEntryType;
import com.urbantransport.reporting.domain.LedgerSourceType;
import com.urbantransport.reporting.dto.LedgerReportResponse;
import com.urbantransport.reporting.dto.ProfitReportResponse;
import com.urbantransport.reporting.dto.RouteMapResponse;
import com.urbantransport.reporting.dto.TopRoutesReportResponse;
import com.urbantransport.reporting.dto.WorkshopCategoryReportResponse;
import com.urbantransport.reporting.exception.ApiException;
import com.urbantransport.reporting.exception.ErrorCodes;
import com.urbantransport.reporting.service.AdminReportService;
import com.urbantransport.reporting.service.ReportPdfService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminReportController.class)
@Import(SecurityConfig.class)
class AdminReportControllerTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Guatemala"));
    private static final LocalDate FROM = TODAY.minusDays(30);
    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BUS_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final byte[] PDF = {37, 80, 68, 70, 45};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminReportService adminReportService;

    @MockitoBean
    private ReportPdfService reportPdfService;

    @Test
    void income_asJson_passesFiltersAndReturnsGroups() throws Exception {
        when(adminReportService.income(FROM, TODAY, BRANCH_ID)).thenReturn(ledger(LedgerEntryType.INCOME));

        mockMvc.perform(get("/reports/income").with(systemAdmin())
                        .param("from", FROM.toString())
                        .param("to", TODAY.toString())
                        .param("branchId", BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.branches[0].branchName").value("Xela"))
                .andExpect(jsonPath("$.branches[0].entries[0].concept").value("TICKET_SALE"))
                .andExpect(jsonPath("$.branches[0].subtotal").value(100.0))
                .andExpect(jsonPath("$.total").value(100.0));

        verifyNoInteractions(reportPdfService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "PDF", " Pdf "})
    void income_asPdf_downloadsAttachment(String format) throws Exception {
        LedgerReportResponse report = ledger(LedgerEntryType.INCOME);
        when(adminReportService.income(null, null, null)).thenReturn(report);
        when(reportPdfService.income(report, null, null, null)).thenReturn(PDF);

        mockMvc.perform(get("/reports/income").with(systemAdmin()).param("format", format))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"reporte-ingresos.pdf\""))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void invalidFormat_returnsBadRequestWithoutComputingReport() throws Exception {
        mockMvc.perform(get("/reports/income").with(systemAdmin()).param("format", "xls"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_FORMAT));

        verifyNoInteractions(adminReportService, reportPdfService);
    }

    @Test
    void malformedDate_returnsValidationError() throws Exception {
        mockMvc.perform(get("/reports/income").with(systemAdmin()).param("from", "ayer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message").value(containsString("from")));

        verifyNoInteractions(adminReportService);
    }

    @Test
    void invalidRangeFromService_returnsBadRequest() throws Exception {
        when(adminReportService.income(TODAY, FROM, null)).thenThrow(new ApiException(HttpStatus.BAD_REQUEST,
                ErrorCodes.INVALID_DATE_RANGE, "Rango invalido"));

        mockMvc.perform(get("/reports/income").with(systemAdmin())
                        .param("from", TODAY.toString())
                        .param("to", FROM.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_DATE_RANGE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/reports/income", "/reports/expenses", "/reports/profit", "/reports/top-routes",
            "/reports/workshop-by-category"})
    void branchAdmin_isDenied(String path) throws Exception {
        mockMvc.perform(get(path).with(user("BRANCH_ADMIN", BRANCH_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(adminReportService, reportPdfService);
    }

    @Test
    void withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/reports/income"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminReportService);
    }

    @Test
    void expenses_asJsonAndPdf() throws Exception {
        LedgerReportResponse report = ledger(LedgerEntryType.EXPENSE);
        when(adminReportService.expenses(FROM, null, null)).thenReturn(report);
        when(reportPdfService.expenses(report, FROM, null, null)).thenReturn(PDF);

        mockMvc.perform(get("/reports/expenses").with(systemAdmin()).param("from", FROM.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EXPENSE"));
        mockMvc.perform(get("/reports/expenses").with(systemAdmin()).param("from", FROM.toString())
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"reporte-gastos.pdf\""));
    }

    @Test
    void profit_asJsonAndPdf() throws Exception {
        ProfitReportResponse report = new ProfitReportResponse(List.of(new ProfitReportResponse.BranchProfit(
                BRANCH_ID, "Xela", new BigDecimal("500.00"), new BigDecimal("200.00"), new BigDecimal("300.00"))),
                new BigDecimal("500.00"), new BigDecimal("200.00"), new BigDecimal("300.00"));
        when(adminReportService.profit(null, null, BRANCH_ID)).thenReturn(report);
        when(reportPdfService.profit(report, null, null, BRANCH_ID)).thenReturn(PDF);

        mockMvc.perform(get("/reports/profit").with(systemAdmin()).param("branchId", BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branches[0].net").value(300.0))
                .andExpect(jsonPath("$.totalNet").value(300.0));
        mockMvc.perform(get("/reports/profit").with(systemAdmin()).param("branchId", BRANCH_ID.toString())
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"reporte-ganancias.pdf\""));
    }

    @Test
    void topRoutes_asJsonAndPdf() throws Exception {
        TopRoutesReportResponse report = new TopRoutesReportResponse(List.of(new TopRoutesReportResponse.RouteDemand(
                UUID.randomUUID(), BRANCH_ID, "Xela", UUID.randomUUID(), "Guatemala", new BigDecimal("200.00"),
                new BigDecimal("75.00"), 2, 12, new BigDecimal("900.00"))), 12, new BigDecimal("900.00"));
        when(adminReportService.topRoutes(FROM, TODAY)).thenReturn(report);
        when(reportPdfService.topRoutes(report, FROM, TODAY)).thenReturn(PDF);

        mockMvc.perform(get("/reports/top-routes").with(systemAdmin())
                        .param("from", FROM.toString()).param("to", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes[0].ticketsSold").value(12))
                .andExpect(jsonPath("$.totalTickets").value(12));
        mockMvc.perform(get("/reports/top-routes").with(systemAdmin())
                        .param("from", FROM.toString()).param("to", TODAY.toString()).param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"reporte-rutas-mas-demandadas.pdf\""));
    }

    @Test
    void workshopByCategory_asJsonAndPdf() throws Exception {
        WorkshopCategoryReportResponse report = new WorkshopCategoryReportResponse(List.of(
                new WorkshopCategoryReportResponse.CategoryGroup(UUID.randomUUID(), "Motor", List.of(),
                        new BigDecimal("400.00"))), new BigDecimal("400.00"));
        when(adminReportService.workshopByCategory(null, null, BRANCH_ID, BUS_ID)).thenReturn(report);
        when(reportPdfService.workshopByCategory(report, null, null, BRANCH_ID, BUS_ID)).thenReturn(PDF);

        mockMvc.perform(get("/reports/workshop-by-category").with(systemAdmin())
                        .param("branchId", BRANCH_ID.toString()).param("busId", BUS_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].categoryName").value("Motor"))
                .andExpect(jsonPath("$.total").value(400.0));
        mockMvc.perform(get("/reports/workshop-by-category").with(systemAdmin())
                        .param("branchId", BRANCH_ID.toString()).param("busId", BUS_ID.toString())
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"reporte-taller-por-categoria.pdf\""));
    }

    @Test
    void routeMap_asJsonAndPdf() throws Exception {
        RouteMapResponse report = new RouteMapResponse(BRANCH_ID, "Xela", List.of(), "La sucursal no tiene rutas");
        when(adminReportService.routeMap(BRANCH_ID)).thenReturn(report);
        when(reportPdfService.routeMap(report)).thenReturn(PDF);

        mockMvc.perform(get("/reports/route-map").with(systemAdmin()).param("branchId", BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchName").value("Xela"))
                .andExpect(jsonPath("$.routes").isEmpty())
                .andExpect(jsonPath("$.message").value("La sucursal no tiene rutas"));
        mockMvc.perform(get("/reports/route-map").with(systemAdmin()).param("branchId", BRANCH_ID.toString())
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"mapa-de-rutas.pdf\""));
    }

    @Test
    void routeMap_withoutBranch_returnsValidationError() throws Exception {
        mockMvc.perform(get("/reports/route-map").with(systemAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));

        verify(adminReportService, never()).routeMap(any());
    }

    @Test
    void routeMap_unknownBranch_returnsNotFound() throws Exception {
        when(adminReportService.routeMap(BRANCH_ID)).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
                ErrorCodes.BRANCH_NOT_FOUND, "Sucursal no encontrada"));

        mockMvc.perform(get("/reports/route-map").with(systemAdmin()).param("branchId", BRANCH_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_NOT_FOUND));
    }

    private static LedgerReportResponse ledger(LedgerEntryType type) {
        return new LedgerReportResponse(type, List.of(new LedgerReportResponse.BranchGroup(BRANCH_ID, "Xela",
                List.of(new LedgerReportResponse.Entry(UUID.randomUUID(), TODAY, LedgerConcept.TICKET_SALE,
                        LedgerSourceType.TICKET_SALE, UUID.randomUUID(), "Venta", new BigDecimal("100.00"))),
                new BigDecimal("100.00"))), new BigDecimal("100.00"));
    }

    private static RequestPostProcessor systemAdmin() {
        return user("SYSTEM_ADMIN", null);
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
