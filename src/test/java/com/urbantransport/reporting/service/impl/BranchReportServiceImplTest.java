package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.BusStatus;
import com.urbantransport.reporting.domain.DimBranchEntity;
import com.urbantransport.reporting.domain.DimBusEntity;
import com.urbantransport.reporting.domain.DimDepreciationRateEntity;
import com.urbantransport.reporting.domain.DimDriverEntity;
import com.urbantransport.reporting.domain.DimRouteEntity;
import com.urbantransport.reporting.domain.FactDepreciationEntity;
import com.urbantransport.reporting.domain.FactRentalEntity;
import com.urbantransport.reporting.domain.FactTicketSaleEntity;
import com.urbantransport.reporting.domain.FactTripEntity;
import com.urbantransport.reporting.domain.FactWorkshopLineEntity;
import com.urbantransport.reporting.dto.BusDepreciationReportResponse;
import com.urbantransport.reporting.dto.BusListReportResponse;
import com.urbantransport.reporting.dto.DriverListReportResponse;
import com.urbantransport.reporting.dto.RentalReportResponse;
import com.urbantransport.reporting.dto.TicketSalesReportResponse;
import com.urbantransport.reporting.dto.WorkshopBusReportResponse;
import com.urbantransport.reporting.exception.ApiException;
import com.urbantransport.reporting.exception.ErrorCodes;
import com.urbantransport.reporting.repository.DimBranchRepository;
import com.urbantransport.reporting.repository.DimBusRepository;
import com.urbantransport.reporting.repository.DimDepreciationRateRepository;
import com.urbantransport.reporting.repository.DimDriverRepository;
import com.urbantransport.reporting.repository.DimRouteRepository;
import com.urbantransport.reporting.repository.FactDepreciationRepository;
import com.urbantransport.reporting.repository.FactRentalRepository;
import com.urbantransport.reporting.repository.FactTicketSaleRepository;
import com.urbantransport.reporting.repository.FactTripRepository;
import com.urbantransport.reporting.repository.FactWorkshopLineRepository;
import com.urbantransport.reporting.repository.IdCountView;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchReportServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final LocalDate TODAY = LocalDate.now(ZONE);
    private static final Instant NOW = Instant.now();
    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DESTINATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111112");
    private static final UUID BUS_1 = UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final UUID BUS_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_3 = UUID.fromString("22222222-2222-2222-2222-222222222223");
    private static final UUID DRIVER_1 = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final UUID DRIVER_2 = UUID.fromString("33333333-3333-3333-3333-333333333332");
    private static final UUID DRIVER_3 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DRIVER_4 = UUID.fromString("33333333-3333-3333-3333-333333333334");
    private static final UUID DRIVER_5 = UUID.fromString("33333333-3333-3333-3333-333333333335");
    private static final UUID DRIVER_6 = UUID.fromString("33333333-3333-3333-3333-333333333336");
    private static final UUID TRIP_1 = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final UUID TRIP_2 = UUID.fromString("44444444-4444-4444-4444-444444444442");
    private static final UUID TRIP_3 = UUID.fromString("44444444-4444-4444-4444-444444444443");
    private static final UUID ROUTE_1 = UUID.fromString("55555555-5555-5555-5555-555555555551");
    private static final UUID ROUTE_2 = UUID.fromString("55555555-5555-5555-5555-555555555552");
    private static final UUID MOTOR_ID = UUID.fromString("66666666-6666-6666-6666-666666666661");
    private static final UUID BRAKES_ID = UUID.fromString("66666666-6666-6666-6666-666666666662");

    @Mock
    private DimBusRepository dimBusRepository;

    @Mock
    private DimDriverRepository dimDriverRepository;

    @Mock
    private DimRouteRepository dimRouteRepository;

    @Mock
    private DimBranchRepository dimBranchRepository;

    @Mock
    private DimDepreciationRateRepository dimDepreciationRateRepository;

    @Mock
    private FactTripRepository factTripRepository;

    @Mock
    private FactTicketSaleRepository factTicketSaleRepository;

    @Mock
    private FactRentalRepository factRentalRepository;

    @Mock
    private FactDepreciationRepository factDepreciationRepository;

    @Mock
    private FactWorkshopLineRepository factWorkshopLineRepository;

    private BranchReportServiceImpl branchReportService;

    @BeforeEach
    void setUp() {
        branchReportService = new BranchReportServiceImpl(dimBusRepository, dimDriverRepository, dimRouteRepository,
                dimBranchRepository, dimDepreciationRateRepository, factTripRepository, factTicketSaleRepository,
                factRentalRepository, factDepreciationRepository, factWorkshopLineRepository);
        ReflectionTestUtils.setField(branchReportService, "zone", ZONE);
    }

    @Nested
    class Buses {

        @Test
        void branchWithoutBuses_returnsEmptyWithoutQueryingTrips() {
            when(dimBusRepository.findByBranchIdOrderByPlateAsc(BRANCH_ID)).thenReturn(List.of());

            BusListReportResponse report = branchReportService.buses(BRANCH_ID, null);

            assertTrue(report.getBuses().isEmpty());
            assertEquals(0, report.getTotalTrips());
            verifyNoInteractions(factTripRepository, dimDriverRepository);
        }

        @Test
        void statusFilter_usesStatusQuery() {
            when(dimBusRepository.findByBranchIdAndStatusOrderByPlateAsc(BRANCH_ID, "EN_TALLER"))
                    .thenReturn(List.of());

            branchReportService.buses(BRANCH_ID, BusStatus.EN_TALLER);

            verify(dimBusRepository, never()).findByBranchIdOrderByPlateAsc(any());
        }

        @Test
        void buses_showCurrentDriverFromTripInTransitOrNextScheduledAndCompletedTrips() {
            when(dimBusRepository.findByBranchIdOrderByPlateAsc(BRANCH_ID)).thenReturn(List.of(
                    bus(BUS_1, "P-111AAA"), bus(BUS_2, "P-222BBB"), bus(BUS_3, "P-333CCC")));
            when(factTripRepository.countByBus(List.of(BUS_1, BUS_2, BUS_3), "COMPLETED")).thenReturn(List.of(
                    new IdCount(BUS_1, 3L), new IdCount(BUS_2, 1L), new IdCount(null, 9L)));
            when(factTripRepository.findByBusIdInAndStatusIn(List.of(BUS_1, BUS_2, BUS_3),
                    List.of("IN_TRANSIT", "SCHEDULED"))).thenReturn(List.of(
                    trip(BUS_1, DRIVER_2, "SCHEDULED", NOW.plusSeconds(172_800)),
                    trip(BUS_1, DRIVER_1, "IN_TRANSIT", NOW.minusSeconds(3_600)),
                    trip(BUS_1, DRIVER_3, "SCHEDULED", NOW.plusSeconds(86_400)),
                    trip(BUS_2, DRIVER_5, "SCHEDULED", null),
                    trip(BUS_2, DRIVER_3, "SCHEDULED", NOW.plusSeconds(259_200)),
                    trip(BUS_2, DRIVER_6, "SCHEDULED", null),
                    trip(BUS_2, DRIVER_4, "SCHEDULED", NOW.plusSeconds(86_400)),
                    trip(BUS_3, null, "SCHEDULED", NOW.plusSeconds(3_600))));
            when(dimDriverRepository.findAllById(any())).thenReturn(List.of(
                    DimDriverEntity.builder().driverId(DRIVER_1).fullName("Juan Pérez").build(),
                    DimDriverEntity.builder().driverId(DRIVER_4).build()));

            BusListReportResponse report = branchReportService.buses(BRANCH_ID, null);

            List<BusListReportResponse.BusRow> rows = report.getBuses();
            assertEquals(3, rows.size());
            BusListReportResponse.BusRow first = rows.get(0);
            assertEquals("P-111AAA", first.getPlate());
            assertEquals("Volvo", first.getBrand());
            assertEquals("B8R", first.getModel());
            assertEquals(40, first.getCapacity());
            assertEquals("DISPONIBLE", first.getStatus());
            assertEquals(new BigDecimal("1500.00"), first.getOdometerKm());
            assertEquals(DRIVER_1, first.getCurrentDriverId());
            assertEquals("Juan Pérez", first.getCurrentDriverName());
            assertEquals(3, first.getCompletedTrips());
            assertEquals(DRIVER_4, rows.get(1).getCurrentDriverId());
            assertNull(rows.get(1).getCurrentDriverName());
            assertEquals(1, rows.get(1).getCompletedTrips());
            assertNull(rows.get(2).getCurrentDriverId());
            assertEquals(0, rows.get(2).getCompletedTrips());
            assertEquals(4, report.getTotalTrips());
        }
    }

    @Nested
    class Drivers {

        @Test
        void branchWithoutDrivers_returnsEmptyWithoutCounting() {
            when(dimDriverRepository.findByBranchIdOrderByFullNameAsc(BRANCH_ID)).thenReturn(List.of());

            DriverListReportResponse report = branchReportService.drivers(BRANCH_ID);

            assertTrue(report.getDrivers().isEmpty());
            assertEquals(0, report.getTotalTrips());
            verifyNoInteractions(factTripRepository);
        }

        @Test
        void drivers_includeLicenseDataAndCompletedTrips() {
            LocalDate expiresOn = TODAY.plusMonths(6);
            when(dimDriverRepository.findByBranchIdOrderByFullNameAsc(BRANCH_ID)).thenReturn(List.of(
                    DimDriverEntity.builder().driverId(DRIVER_1).fullName("Ana López").licenseNumber("A-1")
                            .licenseType("A").licenseExpiresOn(expiresOn).active(true).build(),
                    DimDriverEntity.builder().driverId(DRIVER_2).fullName("Luis Paz").licenseNumber("B-2")
                            .licenseType("B").active(false).build()));
            when(factTripRepository.countByDriver(List.of(DRIVER_1, DRIVER_2), "COMPLETED"))
                    .thenReturn(List.of(new IdCount(DRIVER_1, 5L)));

            DriverListReportResponse report = branchReportService.drivers(BRANCH_ID);

            DriverListReportResponse.DriverRow ana = report.getDrivers().get(0);
            assertEquals(DRIVER_1, ana.getDriverId());
            assertEquals("A-1", ana.getLicenseNumber());
            assertEquals("Ana López", ana.getFullName());
            assertEquals("A", ana.getLicenseType());
            assertEquals(expiresOn, ana.getLicenseExpiresOn());
            assertTrue(ana.getActive());
            assertEquals(5, ana.getCompletedTrips());
            assertEquals(0, report.getDrivers().get(1).getCompletedTrips());
            assertEquals(5, report.getTotalTrips());
        }
    }

    @Nested
    class TicketSales {

        @Test
        void sales_areGroupedByTripOrderedByDepartureWithRouteFallback() {
            when(factTicketSaleRepository.findAll(ArgumentMatchers.<Specification<FactTicketSaleEntity>>any()))
                    .thenReturn(List.of(
                            sale(TRIP_1, ROUTE_1, BUS_1, "50.00"),
                            sale(TRIP_1, ROUTE_1, BUS_1, "50.00"),
                            sale(TRIP_2, ROUTE_2, null, "60.00"),
                            sale(TRIP_3, null, null, "70.00"),
                            sale(null, ROUTE_1, BUS_1, "999.00")));
            when(factTripRepository.findAllById(any())).thenReturn(List.of(
                    FactTripEntity.builder().tripId(TRIP_1).originBranchId(BRANCH_ID)
                            .destinationBranchId(DESTINATION_ID).scheduledDeparture(NOW.plusSeconds(86_400)).build(),
                    FactTripEntity.builder().tripId(TRIP_2).scheduledDeparture(NOW).build()));
            when(dimRouteRepository.findAllById(any())).thenReturn(List.of(
                    DimRouteEntity.builder().routeId(ROUTE_1).originBranchId(BRANCH_ID)
                            .destinationBranchId(DESTINATION_ID).build(),
                    DimRouteEntity.builder().routeId(ROUTE_2).originBranchId(DESTINATION_ID)
                            .destinationBranchId(BRANCH_ID).build()));
            when(dimBranchRepository.findAll()).thenReturn(List.of(
                    DimBranchEntity.builder().branchId(BRANCH_ID).name("Xela").build(),
                    DimBranchEntity.builder().branchId(DESTINATION_ID).name("Guatemala").build(),
                    DimBranchEntity.builder().branchId(UUID.randomUUID()).build()));
            when(dimBusRepository.findAllById(any())).thenReturn(List.of(bus(BUS_1, "P-111AAA")));

            TicketSalesReportResponse report = branchReportService.ticketSales(BRANCH_ID, TODAY.minusDays(7), TODAY,
                    null, null);

            List<TicketSalesReportResponse.TripSales> rows = report.getTrips();
            assertEquals(List.of(TRIP_2, TRIP_1, TRIP_3),
                    rows.stream().map(TicketSalesReportResponse.TripSales::getTripId).toList());
            TicketSalesReportResponse.TripSales routeFallback = rows.get(0);
            assertEquals("Guatemala", routeFallback.getOriginName());
            assertEquals("Xela", routeFallback.getDestinationName());
            assertNull(routeFallback.getPlate());
            TicketSalesReportResponse.TripSales trip1 = rows.get(1);
            assertEquals(ROUTE_1, trip1.getRouteId());
            assertEquals("Xela", trip1.getOriginName());
            assertEquals("Guatemala", trip1.getDestinationName());
            assertEquals(NOW.plusSeconds(86_400), trip1.getDepartureAt());
            assertEquals(BUS_1, trip1.getBusId());
            assertEquals("P-111AAA", trip1.getPlate());
            assertEquals(2, trip1.getTicketsSold());
            assertEquals(new BigDecimal("100.00"), trip1.getIncome());
            TicketSalesReportResponse.TripSales unknown = rows.get(2);
            assertNull(unknown.getOriginName());
            assertNull(unknown.getDepartureAt());
            assertEquals(4, report.getTotalTickets());
            assertEquals(new BigDecimal("230.00"), report.getTotalIncome());
        }

        @Test
        void invalidRange_throwsBadRequest() {
            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE,
                    () -> branchReportService.ticketSales(BRANCH_ID, TODAY, TODAY.minusDays(1), null, null));

            verifyNoInteractions(factTicketSaleRepository);
        }
    }

    @Nested
    class Rentals {

        @Test
        void paidRentals_includeAssignedBusPlateAndTotal() {
            when(factRentalRepository.findAll(ArgumentMatchers.<Specification<FactRentalEntity>>any(),
                    any(Sort.class))).thenReturn(List.of(
                    rental(BUS_1, "1000.00"),
                    rental(null, "250.50"),
                    rental(BUS_2, null)));
            when(dimBusRepository.findAllById(any())).thenReturn(List.of(bus(BUS_1, "P-111AAA")));

            RentalReportResponse report = branchReportService.rentals(BRANCH_ID, null, null);

            List<RentalReportResponse.RentalRow> rows = report.getRentals();
            assertEquals(2, rows.size());
            RentalReportResponse.RentalRow first = rows.get(0);
            assertEquals("COUNTER", first.getChannel());
            assertEquals("CF", first.getCustomerNit());
            assertEquals("Xela", first.getOriginText());
            assertEquals("Tikal", first.getDestinationText());
            assertEquals(TODAY.plusDays(3), first.getDepartureDate());
            assertNull(first.getReturnDate());
            assertEquals(TODAY, first.getPaymentDate());
            assertEquals("P-111AAA", first.getPlate());
            assertNull(rows.get(1).getBusId());
            assertNull(rows.get(1).getPlate());
            assertEquals(new BigDecimal("1250.50"), report.getTotal());

            ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
            verify(factRentalRepository).findAll(ArgumentMatchers.<Specification<FactRentalEntity>>any(),
                    sortCaptor.capture());
            assertEquals(Sort.by("paymentDate", "departureDate"), sortCaptor.getValue());
        }

        @Test
        void invalidRange_throwsBadRequest() {
            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE,
                    () -> branchReportService.rentals(BRANCH_ID, TODAY, TODAY.minusDays(1)));

            verifyNoInteractions(factRentalRepository);
        }
    }

    @Nested
    class BusDepreciation {

        @Test
        void withoutRateOrBuses_returnsEmptyWithNullRate() {
            when(dimDepreciationRateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any()))
                    .thenReturn(Optional.empty());
            when(dimBusRepository.findByBranchIdOrderByPlateAsc(BRANCH_ID)).thenReturn(List.of());

            BusDepreciationReportResponse report = branchReportService.busDepreciation(BRANCH_ID, null, null);

            assertNull(report.getAmountPerKm());
            assertNull(report.getRateEffectiveFrom());
            assertTrue(report.getBuses().isEmpty());
            assertEquals(new BigDecimal("0.00"), report.getTotalKm());
            assertEquals(new BigDecimal("0.00"), report.getTotalDepreciation());
            verifyNoInteractions(factDepreciationRepository);
        }

        @Test
        void everyBus_showsKmCurrentRateAndAccumulatedDepreciation() {
            Instant effectiveFrom = TODAY.minusMonths(1).atStartOfDay(ZONE).toInstant();
            when(dimDepreciationRateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any()))
                    .thenReturn(Optional.of(DimDepreciationRateEntity.builder().amountPerKm(new BigDecimal("0.7500"))
                            .effectiveFrom(effectiveFrom).build()));
            when(dimBusRepository.findByBranchIdOrderByPlateAsc(BRANCH_ID)).thenReturn(List.of(
                    bus(BUS_1, "P-111AAA"), bus(BUS_2, "P-222BBB")));
            when(factDepreciationRepository.findAll(ArgumentMatchers.<Specification<FactDepreciationEntity>>any()))
                    .thenReturn(List.of(
                            depreciation(BUS_1, "100.00", "75.00"),
                            depreciation(BUS_1, "20.50", "15.38"),
                            depreciation(BUS_1, null, null)));

            BusDepreciationReportResponse report = branchReportService.busDepreciation(BRANCH_ID,
                    TODAY.minusDays(30), TODAY);

            assertEquals(new BigDecimal("0.7500"), report.getAmountPerKm());
            assertEquals(effectiveFrom, report.getRateEffectiveFrom());
            BusDepreciationReportResponse.BusDepreciation first = report.getBuses().get(0);
            assertEquals(BUS_1, first.getBusId());
            assertEquals("P-111AAA", first.getPlate());
            assertEquals(new BigDecimal("120.50"), first.getKm());
            assertEquals(new BigDecimal("0.7500"), first.getAmountPerKm());
            assertEquals(new BigDecimal("90.38"), first.getAccumulated());
            BusDepreciationReportResponse.BusDepreciation second = report.getBuses().get(1);
            assertEquals(new BigDecimal("0.00"), second.getKm());
            assertEquals(new BigDecimal("0.00"), second.getAccumulated());
            assertEquals(new BigDecimal("120.50"), report.getTotalKm());
            assertEquals(new BigDecimal("90.38"), report.getTotalDepreciation());
        }

        @Test
        void invalidRange_throwsBadRequest() {
            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE,
                    () -> branchReportService.busDepreciation(BRANCH_ID, TODAY, TODAY.minusDays(1)));

            verifyNoInteractions(dimBusRepository, factDepreciationRepository);
        }
    }

    @Nested
    class WorkshopByBus {

        @Test
        void buses_areOrderedByTotalWithCategorySubtotals() {
            when(factWorkshopLineRepository.findAll(ArgumentMatchers.<Specification<FactWorkshopLineEntity>>any()))
                    .thenReturn(List.of(
                            line(BUS_1, BRAKES_ID, "Frenos", "50.00"),
                            line(BUS_1, MOTOR_ID, "Motor", "100.00"),
                            line(BUS_2, BRAKES_ID, "Frenos", "500.00"),
                            line(BUS_1, MOTOR_ID, "Motor", "300.00"),
                            line(null, MOTOR_ID, "Motor", "10.00")));
            when(dimBusRepository.findAllById(any())).thenReturn(List.of(
                    bus(BUS_1, "P-111AAA"), bus(BUS_2, "P-222BBB")));

            WorkshopBusReportResponse report = branchReportService.workshopByBus(BRANCH_ID, TODAY.minusDays(30),
                    TODAY);

            List<WorkshopBusReportResponse.BusGroup> buses = report.getBuses();
            assertEquals(3, buses.size());
            assertEquals("P-222BBB", buses.get(0).getPlate());
            assertEquals(new BigDecimal("500.00"), buses.get(0).getTotal());
            WorkshopBusReportResponse.BusGroup bus1 = buses.get(1);
            assertEquals(BUS_1, bus1.getBusId());
            assertEquals(new BigDecimal("450.00"), bus1.getTotal());
            assertEquals(2, bus1.getCategories().size());
            WorkshopBusReportResponse.CategoryTotal motor = bus1.getCategories().get(0);
            assertEquals(MOTOR_ID, motor.getCategoryId());
            assertEquals("Motor", motor.getCategoryName());
            assertEquals(2, motor.getLines());
            assertEquals(new BigDecimal("400.00"), motor.getAmount());
            assertEquals("Frenos", bus1.getCategories().get(1).getCategoryName());
            assertNull(buses.get(2).getBusId());
            assertNull(buses.get(2).getPlate());
            assertEquals(new BigDecimal("960.00"), report.getTotal());
        }

        @Test
        void invalidRange_throwsBadRequest() {
            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE,
                    () -> branchReportService.workshopByBus(BRANCH_ID, TODAY, TODAY.minusDays(1)));

            verifyNoInteractions(factWorkshopLineRepository);
        }
    }

    private record IdCount(UUID getId, Long getTotal) implements IdCountView {
    }

    private static DimBusEntity bus(UUID busId, String plate) {
        return DimBusEntity.builder()
                .busId(busId)
                .branchId(BRANCH_ID)
                .plate(plate)
                .brand("Volvo")
                .model("B8R")
                .capacity(40)
                .status("DISPONIBLE")
                .odometerKm(new BigDecimal("1500.00"))
                .build();
    }

    private static FactTripEntity trip(UUID busId, UUID driverId, String status, Instant departure) {
        return FactTripEntity.builder()
                .tripId(UUID.randomUUID())
                .busId(busId)
                .driverId(driverId)
                .status(status)
                .scheduledDeparture(departure)
                .build();
    }

    private static FactTicketSaleEntity sale(UUID tripId, UUID routeId, UUID busId, String price) {
        return FactTicketSaleEntity.builder()
                .ticketId(UUID.randomUUID())
                .tripId(tripId)
                .routeId(routeId)
                .busId(busId)
                .branchId(BRANCH_ID)
                .pricePaid(new BigDecimal(price))
                .paymentDate(TODAY)
                .build();
    }

    private static FactRentalEntity rental(UUID busId, String amount) {
        return FactRentalEntity.builder()
                .rentalId(UUID.randomUUID())
                .branchId(BRANCH_ID)
                .channel("COUNTER")
                .customerNit("CF")
                .originText("Xela")
                .destinationText("Tikal")
                .departureDate(TODAY.plusDays(3))
                .paymentDate(TODAY)
                .busId(busId)
                .amount(amount == null ? null : new BigDecimal(amount))
                .build();
    }

    private static FactDepreciationEntity depreciation(UUID busId, String km, String amount) {
        return FactDepreciationEntity.builder()
                .tripId(UUID.randomUUID())
                .busId(busId)
                .km(km == null ? null : new BigDecimal(km))
                .amount(amount == null ? null : new BigDecimal(amount))
                .build();
    }

    private static FactWorkshopLineEntity line(UUID busId, UUID categoryId, String categoryName, String amount) {
        return FactWorkshopLineEntity.builder()
                .lineId(UUID.randomUUID())
                .busId(busId)
                .branchId(BRANCH_ID)
                .serviceDate(TODAY)
                .categoryId(categoryId)
                .categoryName(categoryName)
                .type("PART")
                .amount(new BigDecimal(amount))
                .build();
    }

    private static void assertApiError(HttpStatus status, String code, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }
}
