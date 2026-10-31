package com.urbantransport.reporting.web;

import com.urbantransport.reporting.AbstractIntegrationTest;
import com.urbantransport.reporting.domain.LedgerConcept;
import com.urbantransport.reporting.domain.LedgerEntryType;
import com.urbantransport.reporting.exception.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminReportApiIT extends AbstractIntegrationTest {

    private UUID xelaId;
    private UUID guateId;
    private UUID antiguaId;

    @BeforeEach
    void seedBranches() {
        xelaId = saveBranch("Xela", "14.834700", "-91.518000");
        guateId = saveBranch("Guatemala", "14.634900", "-90.506900");
        antiguaId = saveBranch("Antigua", null, null);
    }

    @Test
    void income_groupsEntriesByBranchAlphabeticallyWithSubtotalsAndDateFilter() throws Exception {
        LocalDate today = today();
        saveLedger(xelaId, LedgerEntryType.INCOME, LedgerConcept.TICKET_SALE, "100.00", today, "Venta 1");
        saveLedger(xelaId, LedgerEntryType.INCOME, LedgerConcept.TICKET_SALE, "-50.00", today, "Cancelacion");
        saveLedger(guateId, LedgerEntryType.INCOME, LedgerConcept.RENTAL, "1000.00", today.minusDays(1), "Alquiler");
        saveLedger(xelaId, LedgerEntryType.EXPENSE, LedgerConcept.FUEL, "300.00", today, "Combustible");
        saveLedger(xelaId, LedgerEntryType.INCOME, LedgerConcept.TICKET_SALE, "999.00", today.minusDays(40), "Viejo");

        mockMvc.perform(get("/reports/income").with(systemAdmin())
                        .param("from", today.minusDays(7).toString())
                        .param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.branches", hasSize(2)))
                .andExpect(jsonPath("$.branches[0].branchName").value("Guatemala"))
                .andExpect(jsonPath("$.branches[0].entries[0].concept").value("RENTAL"))
                .andExpect(jsonPath("$.branches[0].subtotal").value(1000.0))
                .andExpect(jsonPath("$.branches[1].branchName").value("Xela"))
                .andExpect(jsonPath("$.branches[1].entries", hasSize(2)))
                .andExpect(jsonPath("$.branches[1].subtotal").value(50.0))
                .andExpect(jsonPath("$.total").value(1050.0));
    }

    @Test
    void income_withBranchFilter_returnsOnlyThatBranch() throws Exception {
        saveLedger(xelaId, LedgerEntryType.INCOME, LedgerConcept.TICKET_SALE, "100.00", today(), "Venta");
        saveLedger(guateId, LedgerEntryType.INCOME, LedgerConcept.RENTAL, "1000.00", today(), "Alquiler");

        mockMvc.perform(get("/reports/income").with(systemAdmin()).param("branchId", xelaId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branches", hasSize(1)))
                .andExpect(jsonPath("$.branches[0].branchId").value(xelaId.toString()))
                .andExpect(jsonPath("$.total").value(100.0));
    }

    @Test
    void income_asPdf_returnsDocumentAttachment() throws Exception {
        saveLedger(xelaId, LedgerEntryType.INCOME, LedgerConcept.TICKET_SALE, "100.00", today(), "Venta");

        MvcResult result = mockMvc.perform(get("/reports/income").with(systemAdmin()).param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"reporte-ingresos.pdf\""))
                .andReturn();

        assertPdf(result);
    }

    @Test
    void emptyReportAsPdf_stillReturnsDocument() throws Exception {
        MvcResult result = mockMvc.perform(get("/reports/expenses").with(systemAdmin())
                        .param("from", today().minusYears(5).toString())
                        .param("to", today().minusYears(4).toString())
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        assertPdf(result);
    }

    @Test
    void expenses_includeEveryExpenseConceptAndUnassignedBranchLast() throws Exception {
        saveLedger(xelaId, LedgerEntryType.EXPENSE, LedgerConcept.FUEL, "300.00", today(), "Combustible");
        saveLedger(xelaId, LedgerEntryType.EXPENSE, LedgerConcept.SPARE_PARTS, "150.00", today(), "Repuestos");
        saveLedger(null, LedgerEntryType.EXPENSE, LedgerConcept.DEPRECIATION, "20.00", today(), "Depreciacion");
        saveLedger(xelaId, LedgerEntryType.INCOME, LedgerConcept.TICKET_SALE, "100.00", today(), "Venta");

        mockMvc.perform(get("/reports/expenses").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.branches[0].branchName").value("Xela"))
                .andExpect(jsonPath("$.branches[0].entries[1].concept").value("SPARE_PARTS"))
                .andExpect(jsonPath("$.branches[1].branchId").doesNotExist())
                .andExpect(jsonPath("$.branches[1].branchName").value("Sin sucursal asignada"))
                .andExpect(jsonPath("$.total").value(470.0));
    }

    @Test
    void profit_listsEveryBranchAlphabeticallyWithNetAndTotals() throws Exception {
        saveLedger(xelaId, LedgerEntryType.INCOME, LedgerConcept.TICKET_SALE, "500.00", today(), "Venta");
        saveLedger(xelaId, LedgerEntryType.EXPENSE, LedgerConcept.FUEL, "200.00", today(), "Combustible");
        saveLedger(guateId, LedgerEntryType.EXPENSE, LedgerConcept.WORKSHOP, "100.00", today(), "Taller");

        mockMvc.perform(get("/reports/profit").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branches", hasSize(3)))
                .andExpect(jsonPath("$.branches[0].branchName").value("Antigua"))
                .andExpect(jsonPath("$.branches[0].net").value(0.0))
                .andExpect(jsonPath("$.branches[1].branchName").value("Guatemala"))
                .andExpect(jsonPath("$.branches[1].net").value(-100.0))
                .andExpect(jsonPath("$.branches[2].income").value(500.0))
                .andExpect(jsonPath("$.branches[2].expense").value(200.0))
                .andExpect(jsonPath("$.branches[2].net").value(300.0))
                .andExpect(jsonPath("$.totalIncome").value(500.0))
                .andExpect(jsonPath("$.totalExpense").value(300.0))
                .andExpect(jsonPath("$.totalNet").value(200.0));

        mockMvc.perform(get("/reports/profit").with(systemAdmin()).param("branchId", guateId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branches", hasSize(1)))
                .andExpect(jsonPath("$.totalNet").value(-100.0));
    }

    @Test
    void topRoutes_rankByTicketsAndIgnoreCancelledSales() throws Exception {
        UUID busId = saveBus(xelaId, "P-111AAA", "DISPONIBLE");
        UUID xelaGuate = saveRoute(xelaId, guateId, "50.00", false);
        UUID guateXela = saveRoute(guateId, xelaId, "60.00", false);
        UUID trip1 = saveTrip(xelaGuate, xelaId, guateId, busId, null, "COMPLETED", at(today(), 8));
        UUID trip2 = saveTrip(xelaGuate, xelaId, guateId, busId, null, "SCHEDULED", at(today(), 15));
        UUID trip3 = saveTrip(guateXela, guateId, xelaId, busId, null, "SCHEDULED", at(today(), 18));
        saveSale(trip1, xelaGuate, busId, xelaId, "50.00", today(), false);
        saveSale(trip1, xelaGuate, busId, xelaId, "50.00", today(), false);
        saveSale(trip2, xelaGuate, busId, xelaId, "50.00", today(), false);
        saveSale(trip2, xelaGuate, busId, xelaId, "50.00", today(), true);
        saveSale(trip3, guateXela, busId, guateId, "60.00", today(), false);

        mockMvc.perform(get("/reports/top-routes").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes", hasSize(2)))
                .andExpect(jsonPath("$.routes[0].routeId").value(xelaGuate.toString()))
                .andExpect(jsonPath("$.routes[0].originName").value("Xela"))
                .andExpect(jsonPath("$.routes[0].destinationName").value("Guatemala"))
                .andExpect(jsonPath("$.routes[0].trips").value(2))
                .andExpect(jsonPath("$.routes[0].ticketsSold").value(3))
                .andExpect(jsonPath("$.routes[0].income").value(150.0))
                .andExpect(jsonPath("$.routes[1].ticketsSold").value(1))
                .andExpect(jsonPath("$.totalTickets").value(4))
                .andExpect(jsonPath("$.totalIncome").value(210.0));
    }

    @Test
    void workshopByCategory_filtersByBusAndGroupsByCategory() throws Exception {
        UUID bus1 = saveBus(xelaId, "P-111AAA", "DISPONIBLE");
        UUID bus2 = saveBus(xelaId, "P-222BBB", "DISPONIBLE");
        UUID motor = UUID.randomUUID();
        UUID brakes = UUID.randomUUID();
        saveWorkshopLine(xelaId, bus1, motor, "Motor", "LABOR", "300.00", today());
        saveWorkshopLine(xelaId, bus1, brakes, "Frenos", "PART", "50.00", today().minusDays(1));
        saveWorkshopLine(xelaId, bus1, motor, "Motor", "PART", "100.00", today());
        saveWorkshopLine(xelaId, bus2, motor, "Motor", "LABOR", "999.00", today());

        mockMvc.perform(get("/reports/workshop-by-category").with(systemAdmin())
                        .param("busId", bus1.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", hasSize(2)))
                .andExpect(jsonPath("$.categories[0].categoryName").value("Frenos"))
                .andExpect(jsonPath("$.categories[1].categoryName").value("Motor"))
                .andExpect(jsonPath("$.categories[1].lines", hasSize(2)))
                .andExpect(jsonPath("$.categories[1].lines[0].plate").value("P-111AAA"))
                .andExpect(jsonPath("$.categories[1].subtotal").value(400.0))
                .andExpect(jsonPath("$.total").value(450.0));
    }

    @Test
    void routeMap_returnsActiveRoutesWithCoordinates() throws Exception {
        UUID active = saveRoute(xelaId, guateId, "50.00", false);
        saveRoute(xelaId, antiguaId, "40.00", true);
        saveRoute(guateId, xelaId, "60.00", false);

        mockMvc.perform(get("/reports/route-map").with(systemAdmin()).param("branchId", xelaId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchName").value("Xela"))
                .andExpect(jsonPath("$.routes", hasSize(1)))
                .andExpect(jsonPath("$.routes[0].routeId").value(active.toString()))
                .andExpect(jsonPath("$.routes[0].origin.latitude").value(14.8347))
                .andExpect(jsonPath("$.routes[0].origin.longitude").value(-91.518))
                .andExpect(jsonPath("$.routes[0].destination.name").value("Guatemala"))
                .andExpect(jsonPath("$.routes[0].destination.latitude").value(14.6349))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void routeMap_branchWithoutRoutes_returnsEmptyMapWithNotice() throws Exception {
        mockMvc.perform(get("/reports/route-map").with(systemAdmin()).param("branchId", antiguaId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes", hasSize(0)))
                .andExpect(jsonPath("$.message").value("La sucursal no tiene rutas registradas"));

        MvcResult result = mockMvc.perform(get("/reports/route-map").with(systemAdmin())
                        .param("branchId", antiguaId.toString()).param("format", "pdf"))
                .andExpect(status().isOk())
                .andReturn();
        assertPdf(result);
    }

    @Test
    void routeMap_unknownBranch_returnsNotFound() throws Exception {
        mockMvc.perform(get("/reports/route-map").with(systemAdmin()).param("branchId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_NOT_FOUND));
    }

    @Test
    void routeMap_withoutBranch_returnsValidationError() throws Exception {
        mockMvc.perform(get("/reports/route-map").with(systemAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message", containsString("branchId")));
    }

    @Test
    void invalidRange_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/reports/profit").with(systemAdmin())
                        .param("from", today().toString())
                        .param("to", today().minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_DATE_RANGE))
                .andExpect(jsonPath("$.path").value("/reports/profit"));
    }

    @Test
    void malformedDateOrFormat_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/reports/income").with(systemAdmin()).param("to", "31-12-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));

        mockMvc.perform(get("/reports/income").with(systemAdmin()).param("format", "xlsx"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_FORMAT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BRANCH_ADMIN", "CASHIER", "CUSTOMER"})
    void otherRoles_areDenied(String role) throws Exception {
        mockMvc.perform(get("/reports/income").with(userWithRole(role, xelaId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));
    }

    @Test
    void withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/reports/top-routes"))
                .andExpect(status().isUnauthorized());
    }

    private static void assertPdf(MvcResult result) {
        byte[] body = result.getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(body, 0, 4, StandardCharsets.US_ASCII));
    }
}
