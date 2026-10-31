package com.urbantransport.reporting.web;

import com.urbantransport.reporting.AbstractIntegrationTest;
import com.urbantransport.reporting.exception.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BranchReportApiIT extends AbstractIntegrationTest {

    private UUID branchId;
    private UUID otherBranchId;

    @BeforeEach
    void seedBranches() {
        branchId = saveBranch("Xela", "14.834700", "-91.518000");
        otherBranchId = saveBranch("Guatemala", "14.634900", "-90.506900");
    }

    @Test
    void buses_listOwnBranchWithCurrentDriverAndCompletedTrips() throws Exception {
        UUID bus1 = saveBus(branchId, "P-111AAA", "EN_TRANSITO");
        UUID bus2 = saveBus(branchId, "P-222BBB", "DISPONIBLE");
        saveBus(otherBranchId, "P-999ZZZ", "DISPONIBLE");
        UUID driver1 = saveDriver(branchId, "Juan Perez", "A-1", today().plusYears(1), true);
        UUID driver2 = saveDriver(branchId, "Ana Lopez", "A-2", today().plusYears(1), true);
        Instant now = Instant.now();
        saveTrip(null, branchId, otherBranchId, bus1, driver2, "COMPLETED", now.minusSeconds(86_400));
        saveTrip(null, branchId, otherBranchId, bus1, driver2, "COMPLETED", now.minusSeconds(172_800));
        saveTrip(null, branchId, otherBranchId, bus1, driver1, "IN_TRANSIT", now.minusSeconds(3_600));
        saveTrip(null, branchId, otherBranchId, bus2, driver1, "SCHEDULED", now.plusSeconds(172_800));
        saveTrip(null, branchId, otherBranchId, bus2, driver2, "SCHEDULED", now.plusSeconds(86_400));
        saveTrip(null, branchId, otherBranchId, bus2, driver2, "DELETED", now.plusSeconds(3_600));

        mockMvc.perform(get("/reports/buses").with(branchAdmin(branchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buses", hasSize(2)))
                .andExpect(jsonPath("$.buses[0].plate").value("P-111AAA"))
                .andExpect(jsonPath("$.buses[0].status").value("EN_TRANSITO"))
                .andExpect(jsonPath("$.buses[0].currentDriverName").value("Juan Perez"))
                .andExpect(jsonPath("$.buses[0].completedTrips").value(2))
                .andExpect(jsonPath("$.buses[0].odometerKm").value(1500.0))
                .andExpect(jsonPath("$.buses[1].plate").value("P-222BBB"))
                .andExpect(jsonPath("$.buses[1].currentDriverId").value(driver2.toString()))
                .andExpect(jsonPath("$.buses[1].completedTrips").value(0))
                .andExpect(jsonPath("$.totalTrips").value(2));
    }

    @Test
    void buses_filterByStatus() throws Exception {
        saveBus(branchId, "P-111AAA", "EN_TALLER");
        saveBus(branchId, "P-222BBB", "DISPONIBLE");

        mockMvc.perform(get("/reports/buses").with(branchAdmin(branchId)).param("status", "EN_TALLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buses", hasSize(1)))
                .andExpect(jsonPath("$.buses[0].plate").value("P-111AAA"))
                .andExpect(jsonPath("$.buses[0].currentDriverId").doesNotExist());
    }

    @Test
    void buses_withUnknownStatus_returnsValidationError() throws Exception {
        mockMvc.perform(get("/reports/buses").with(branchAdmin(branchId)).param("status", "VOLANDO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));
    }

    @Test
    void branchIdParameter_isIgnoredInFavorOfToken() throws Exception {
        saveBus(otherBranchId, "P-999ZZZ", "DISPONIBLE");

        mockMvc.perform(get("/reports/buses").with(branchAdmin(branchId))
                        .param("branchId", otherBranchId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buses", hasSize(0)));
    }

    @Test
    void drivers_listOwnBranchByNameWithCompletedTrips() throws Exception {
        LocalDate expiresOn = today().plusMonths(2);
        UUID juan = saveDriver(branchId, "Juan Perez", "A-1", expiresOn, true);
        saveDriver(branchId, "Ana Lopez", "B-2", today().minusDays(1), false);
        saveDriver(otherBranchId, "Otro Chofer", "C-3", expiresOn, true);
        saveTrip(null, branchId, otherBranchId, null, juan, "COMPLETED", Instant.now());
        saveTrip(null, branchId, otherBranchId, null, juan, "SCHEDULED", Instant.now());

        mockMvc.perform(get("/reports/drivers").with(branchAdmin(branchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drivers", hasSize(2)))
                .andExpect(jsonPath("$.drivers[0].fullName").value("Ana Lopez"))
                .andExpect(jsonPath("$.drivers[0].active").value(false))
                .andExpect(jsonPath("$.drivers[0].completedTrips").value(0))
                .andExpect(jsonPath("$.drivers[1].licenseNumber").value("A-1"))
                .andExpect(jsonPath("$.drivers[1].licenseType").value("A"))
                .andExpect(jsonPath("$.drivers[1].licenseExpiresOn").value(expiresOn.toString()))
                .andExpect(jsonPath("$.drivers[1].completedTrips").value(1))
                .andExpect(jsonPath("$.totalTrips").value(1));
    }

    @Test
    void ticketSales_groupByTripAndApplyRouteBusAndDateFilters() throws Exception {
        UUID bus1 = saveBus(branchId, "P-111AAA", "DISPONIBLE");
        UUID bus2 = saveBus(branchId, "P-222BBB", "DISPONIBLE");
        UUID route = saveRoute(branchId, otherBranchId, "50.00", false);
        UUID otherRoute = saveRoute(otherBranchId, branchId, "60.00", false);
        UUID trip1 = saveTrip(route, branchId, otherBranchId, bus1, null, "COMPLETED", at(today(), 8));
        UUID trip2 = saveTrip(route, branchId, otherBranchId, bus2, null, "SCHEDULED", at(today(), 15));
        UUID trip3 = saveTrip(otherRoute, otherBranchId, branchId, bus1, null, "SCHEDULED", at(today(), 18));
        saveSale(trip1, route, bus1, branchId, "50.00", today(), false);
        saveSale(trip1, route, bus1, branchId, "50.00", today(), false);
        saveSale(trip1, route, bus1, branchId, "50.00", today(), true);
        saveSale(trip1, route, bus1, branchId, "50.00", today().minusDays(60), false);
        saveSale(trip2, route, bus2, branchId, "50.00", today(), false);
        saveSale(trip3, otherRoute, bus1, otherBranchId, "60.00", today(), false);

        mockMvc.perform(get("/reports/ticket-sales").with(branchAdmin(branchId))
                        .param("from", today().minusDays(7).toString())
                        .param("to", today().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trips", hasSize(2)))
                .andExpect(jsonPath("$.trips[0].tripId").value(trip1.toString()))
                .andExpect(jsonPath("$.trips[0].originName").value("Xela"))
                .andExpect(jsonPath("$.trips[0].destinationName").value("Guatemala"))
                .andExpect(jsonPath("$.trips[0].plate").value("P-111AAA"))
                .andExpect(jsonPath("$.trips[0].ticketsSold").value(2))
                .andExpect(jsonPath("$.trips[0].income").value(100.0))
                .andExpect(jsonPath("$.totalTickets").value(3))
                .andExpect(jsonPath("$.totalIncome").value(150.0));

        mockMvc.perform(get("/reports/ticket-sales").with(branchAdmin(branchId))
                        .param("routeId", route.toString())
                        .param("busId", bus2.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trips", hasSize(1)))
                .andExpect(jsonPath("$.trips[0].tripId").value(trip2.toString()))
                .andExpect(jsonPath("$.totalTickets").value(1));
    }

    @Test
    void rentals_listPaidRentalsOfOwnBranchWithBusPlate() throws Exception {
        UUID busId = saveBus(branchId, "P-111AAA", "DISPONIBLE");
        saveRental(branchId, busId, "1000.00", today());
        saveRental(branchId, null, "250.50", today().minusDays(2));
        saveRental(branchId, busId, null, today());
        saveRental(otherBranchId, busId, "5000.00", today());
        saveRental(branchId, busId, "777.00", today().minusDays(90));

        mockMvc.perform(get("/reports/rentals").with(branchAdmin(branchId))
                        .param("from", today().minusDays(30).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentals", hasSize(2)))
                .andExpect(jsonPath("$.rentals[0].amount").value(250.5))
                .andExpect(jsonPath("$.rentals[0].plate").doesNotExist())
                .andExpect(jsonPath("$.rentals[1].plate").value("P-111AAA"))
                .andExpect(jsonPath("$.rentals[1].customerNit").value("CF"))
                .andExpect(jsonPath("$.rentals[1].destinationText").value("Tikal"))
                .andExpect(jsonPath("$.total").value(1250.5));
    }

    @Test
    void busDepreciation_sumsKmAndAmountPerBusWithCurrentRate() throws Exception {
        saveRate("0.5000", at(today().minusMonths(2), 0));
        saveRate("0.7500", at(today().minusMonths(1), 0));
        saveRate("9.9900", Instant.now().plusSeconds(86_400 * 30L));
        UUID bus1 = saveBus(branchId, "P-111AAA", "DISPONIBLE");
        UUID bus2 = saveBus(branchId, "P-222BBB", "DISPONIBLE");
        UUID foreignBus = saveBus(otherBranchId, "P-999ZZZ", "DISPONIBLE");
        saveDepreciation(bus1, branchId, "100.00", "75.00", at(today(), 10));
        saveDepreciation(bus1, branchId, "20.50", "15.38", at(today().minusDays(3), 10));
        saveDepreciation(bus1, branchId, "500.00", "250.00", at(today().minusDays(60), 10));
        saveDepreciation(foreignBus, otherBranchId, "300.00", "225.00", at(today(), 10));

        mockMvc.perform(get("/reports/bus-depreciation").with(branchAdmin(branchId))
                        .param("from", today().minusDays(7).toString())
                        .param("to", today().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountPerKm").value(0.75))
                .andExpect(jsonPath("$.buses", hasSize(2)))
                .andExpect(jsonPath("$.buses[0].busId").value(bus1.toString()))
                .andExpect(jsonPath("$.buses[0].km").value(120.5))
                .andExpect(jsonPath("$.buses[0].accumulated").value(90.38))
                .andExpect(jsonPath("$.buses[1].busId").value(bus2.toString()))
                .andExpect(jsonPath("$.buses[1].km").value(0.0))
                .andExpect(jsonPath("$.totalKm").value(120.5))
                .andExpect(jsonPath("$.totalDepreciation").value(90.38));
    }

    @Test
    void workshopByBus_ordersBusesByTotalWithCategorySubtotals() throws Exception {
        UUID bus1 = saveBus(branchId, "P-111AAA", "DISPONIBLE");
        UUID bus2 = saveBus(branchId, "P-222BBB", "DISPONIBLE");
        UUID motor = UUID.randomUUID();
        UUID brakes = UUID.randomUUID();
        saveWorkshopLine(branchId, bus1, motor, "Motor", "LABOR", "100.00", today());
        saveWorkshopLine(branchId, bus1, motor, "Motor", "PART", "300.00", today());
        saveWorkshopLine(branchId, bus1, brakes, "Frenos", "PART", "50.00", today());
        saveWorkshopLine(branchId, bus2, brakes, "Frenos", "PART", "500.00", today());
        saveWorkshopLine(otherBranchId, bus2, brakes, "Frenos", "PART", "9999.00", today());

        mockMvc.perform(get("/reports/workshop-by-bus").with(branchAdmin(branchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buses", hasSize(2)))
                .andExpect(jsonPath("$.buses[0].plate").value("P-222BBB"))
                .andExpect(jsonPath("$.buses[0].total").value(500.0))
                .andExpect(jsonPath("$.buses[1].plate").value("P-111AAA"))
                .andExpect(jsonPath("$.buses[1].categories[0].categoryName").value("Motor"))
                .andExpect(jsonPath("$.buses[1].categories[0].lines").value(2))
                .andExpect(jsonPath("$.buses[1].categories[0].amount").value(400.0))
                .andExpect(jsonPath("$.buses[1].total").value(450.0))
                .andExpect(jsonPath("$.total").value(950.0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/reports/buses", "/reports/drivers", "/reports/ticket-sales", "/reports/rentals",
            "/reports/bus-depreciation", "/reports/workshop-by-bus"})
    void everyReport_canBeExportedAsPdf(String path) throws Exception {
        UUID busId = saveBus(branchId, "P-111AAA", "DISPONIBLE");
        saveDriver(branchId, "Juan Perez", "A-1", today().plusYears(1), true);
        saveRental(branchId, busId, "1000.00", today());
        saveWorkshopLine(branchId, busId, UUID.randomUUID(), "Motor", "LABOR", "100.00", today());

        MvcResult result = mockMvc.perform(get(path).with(branchAdmin(branchId)).param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        assertEquals("%PDF", new String(result.getResponse().getContentAsByteArray(), 0, 4,
                StandardCharsets.US_ASCII));
    }

    @Test
    void invalidRange_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/reports/rentals").with(branchAdmin(branchId))
                        .param("from", today().toString())
                        .param("to", today().minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_DATE_RANGE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/reports/buses", "/reports/drivers", "/reports/ticket-sales", "/reports/rentals",
            "/reports/bus-depreciation", "/reports/workshop-by-bus"})
    void systemAdmin_isDenied(String path) throws Exception {
        mockMvc.perform(get(path).with(systemAdmin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));
    }

    @Test
    void branchAdminWithoutBranch_returnsBranchRequired() throws Exception {
        mockMvc.perform(get("/reports/drivers").with(branchAdmin(null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_REQUIRED));
    }

    @Test
    void withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/reports/rentals"))
                .andExpect(status().isUnauthorized());
    }
}
