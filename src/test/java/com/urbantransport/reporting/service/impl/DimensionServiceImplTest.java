package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.DimBranchEntity;
import com.urbantransport.reporting.domain.DimBusEntity;
import com.urbantransport.reporting.domain.DimDriverEntity;
import com.urbantransport.reporting.domain.DimRouteEntity;
import com.urbantransport.reporting.domain.ProcessedEventEntity;
import com.urbantransport.reporting.event.BranchEventPayload;
import com.urbantransport.reporting.event.BusEventPayload;
import com.urbantransport.reporting.event.DriverEventPayload;
import com.urbantransport.reporting.event.EventTypes;
import com.urbantransport.reporting.event.RouteEventPayload;
import com.urbantransport.reporting.repository.DimBranchRepository;
import com.urbantransport.reporting.repository.DimBusRepository;
import com.urbantransport.reporting.repository.DimDriverRepository;
import com.urbantransport.reporting.repository.DimRouteRepository;
import com.urbantransport.reporting.repository.ProcessedEventRepository;
import com.urbantransport.reporting.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DimensionServiceImplTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DRIVER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID ROUTE_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID DESTINATION_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final LocalDate EXPIRES_ON = LocalDate.now(ZoneId.of("America/Guatemala")).plusYears(1);

    @Mock
    private DimBranchRepository dimBranchRepository;

    @Mock
    private DimBusRepository dimBusRepository;

    @Mock
    private DimDriverRepository dimDriverRepository;

    @Mock
    private DimRouteRepository dimRouteRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private LedgerService ledgerService;

    @Captor
    private ArgumentCaptor<ProcessedEventEntity> processedEventCaptor;

    private DimensionServiceImpl dimensionService;

    @BeforeEach
    void setUp() {
        dimensionService = new DimensionServiceImpl(dimBranchRepository, dimBusRepository, dimDriverRepository,
                dimRouteRepository, processedEventRepository, ledgerService);
    }

    @Nested
    class ApplyBranch {

        @Test
        void newBranch_isInsertedWithCoordinates() {
            when(dimBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

            dimensionService.applyBranch(EVENT_ID, EventTypes.BRANCH_CREATED, branchPayload("Central", true));

            ArgumentCaptor<DimBranchEntity> captor = ArgumentCaptor.forClass(DimBranchEntity.class);
            verify(dimBranchRepository).save(captor.capture());
            DimBranchEntity saved = captor.getValue();
            assertEquals(BRANCH_ID, saved.getBranchId());
            assertEquals("Central", saved.getName());
            assertEquals("CEN", saved.getCode());
            assertEquals(new BigDecimal("14.634900"), saved.getLatitude());
            assertEquals(new BigDecimal("-90.506900"), saved.getLongitude());
            assertTrue(saved.getActive());
            assertMarkedProcessed(EventTypes.BRANCH_CREATED);
        }

        @Test
        void existingBranch_isOverwritten() {
            DimBranchEntity existing = DimBranchEntity.builder().branchId(BRANCH_ID).name("Viejo").active(true).build();
            when(dimBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(existing));

            dimensionService.applyBranch(EVENT_ID, EventTypes.BRANCH_UPDATED, branchPayload("Central Norte", false));

            verify(dimBranchRepository).save(existing);
            assertEquals("Central Norte", existing.getName());
            assertFalse(existing.getActive());
            assertMarkedProcessed(EventTypes.BRANCH_UPDATED);
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            dimensionService.applyBranch(EVENT_ID, EventTypes.BRANCH_UPDATED, branchPayload("Central", true));

            verifyNoInteractions(dimBranchRepository);
            verify(processedEventRepository, never()).save(any());
        }
    }

    @Nested
    class ApplyBus {

        @Test
        void newBus_isInsertedAndCompletesPendingLedgerBranch() {
            when(dimBusRepository.findById(BUS_ID)).thenReturn(Optional.empty());

            dimensionService.applyBus(EVENT_ID, EventTypes.BUS_REGISTERED, busPayload(BRANCH_ID, "DISPONIBLE"));

            ArgumentCaptor<DimBusEntity> captor = ArgumentCaptor.forClass(DimBusEntity.class);
            verify(dimBusRepository).save(captor.capture());
            DimBusEntity saved = captor.getValue();
            assertEquals(BUS_ID, saved.getBusId());
            assertEquals(BRANCH_ID, saved.getBranchId());
            assertEquals("P-123ABC", saved.getPlate());
            assertEquals("Volvo", saved.getBrand());
            assertEquals("B8R", saved.getModel());
            assertEquals((short) 2020, saved.getManufactureYear());
            assertEquals(40, saved.getCapacity());
            assertEquals("DISPONIBLE", saved.getStatus());
            assertEquals(new BigDecimal("1500.00"), saved.getOdometerKm());
            verify(ledgerService).assignBusBranch(BUS_ID, BRANCH_ID);
            assertMarkedProcessed(EventTypes.BUS_REGISTERED);
        }

        @Test
        void existingBus_isOverwritten() {
            DimBusEntity existing = DimBusEntity.builder().busId(BUS_ID).status("DISPONIBLE").build();
            when(dimBusRepository.findById(BUS_ID)).thenReturn(Optional.of(existing));

            dimensionService.applyBus(EVENT_ID, EventTypes.BUS_DEACTIVATED, busPayload(BRANCH_ID, "INACTIVO"));

            ArgumentCaptor<DimBusEntity> captor = ArgumentCaptor.forClass(DimBusEntity.class);
            verify(dimBusRepository).save(captor.capture());
            assertSame(existing, captor.getValue());
            assertEquals("INACTIVO", existing.getStatus());
            assertMarkedProcessed(EventTypes.BUS_DEACTIVATED);
        }

        @Test
        void busWithoutBranch_doesNotTouchLedger() {
            when(dimBusRepository.findById(BUS_ID)).thenReturn(Optional.empty());

            dimensionService.applyBus(EVENT_ID, EventTypes.BUS_UPDATED, busPayload(null, "EN_TALLER"));

            verify(dimBusRepository).save(any(DimBusEntity.class));
            verifyNoInteractions(ledgerService);
            assertMarkedProcessed(EventTypes.BUS_UPDATED);
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            dimensionService.applyBus(EVENT_ID, EventTypes.BUS_UPDATED, busPayload(BRANCH_ID, "DISPONIBLE"));

            verifyNoInteractions(dimBusRepository, ledgerService);
            verify(processedEventRepository, never()).save(any());
        }
    }

    @Nested
    class ApplyDriver {

        @Test
        void newDriver_isInserted() {
            when(dimDriverRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());

            dimensionService.applyDriver(EVENT_ID, EventTypes.DRIVER_REGISTERED, driverPayload(true));

            ArgumentCaptor<DimDriverEntity> captor = ArgumentCaptor.forClass(DimDriverEntity.class);
            verify(dimDriverRepository).save(captor.capture());
            DimDriverEntity saved = captor.getValue();
            assertEquals(DRIVER_ID, saved.getDriverId());
            assertEquals(USER_ID, saved.getUserId());
            assertEquals(BRANCH_ID, saved.getBranchId());
            assertEquals("Juan Pérez", saved.getFullName());
            assertEquals("A-123", saved.getLicenseNumber());
            assertEquals("A", saved.getLicenseType());
            assertEquals(EXPIRES_ON, saved.getLicenseExpiresOn());
            assertTrue(saved.getActive());
            assertMarkedProcessed(EventTypes.DRIVER_REGISTERED);
        }

        @Test
        void existingDriver_isOverwritten() {
            DimDriverEntity existing = DimDriverEntity.builder().driverId(DRIVER_ID).active(true).build();
            when(dimDriverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(existing));

            dimensionService.applyDriver(EVENT_ID, EventTypes.DRIVER_DEACTIVATED, driverPayload(false));

            verify(dimDriverRepository).save(existing);
            assertFalse(existing.getActive());
            assertMarkedProcessed(EventTypes.DRIVER_DEACTIVATED);
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            dimensionService.applyDriver(EVENT_ID, EventTypes.DRIVER_UPDATED, driverPayload(true));

            verifyNoInteractions(dimDriverRepository);
            verify(processedEventRepository, never()).save(any());
        }
    }

    @Nested
    class ApplyRoute {

        @ParameterizedTest
        @CsvSource(value = {
                "RouteCreated, false, false",
                "RouteUpdated, , false",
                "RouteUpdated, true, true",
                "RouteDeleted, false, true",
                "RouteDeleted, , true"
        })
        void route_isUpsertedWithDeletedFlag(String eventType, Boolean payloadDeleted, boolean expectedDeleted) {
            when(dimRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.empty());

            dimensionService.applyRoute(EVENT_ID, eventType, new RouteEventPayload(ROUTE_ID, BRANCH_ID, DESTINATION_ID,
                    new BigDecimal("120.50"), new BigDecimal("45.00"), payloadDeleted));

            ArgumentCaptor<DimRouteEntity> captor = ArgumentCaptor.forClass(DimRouteEntity.class);
            verify(dimRouteRepository).save(captor.capture());
            DimRouteEntity saved = captor.getValue();
            assertEquals(ROUTE_ID, saved.getRouteId());
            assertEquals(BRANCH_ID, saved.getOriginBranchId());
            assertEquals(DESTINATION_ID, saved.getDestinationBranchId());
            assertEquals(new BigDecimal("120.50"), saved.getDistanceKm());
            assertEquals(new BigDecimal("45.00"), saved.getTicketPrice());
            assertEquals(expectedDeleted, saved.getDeleted());
            assertMarkedProcessed(eventType);
        }

        @Test
        void existingRoute_isOverwritten() {
            DimRouteEntity existing = DimRouteEntity.builder().routeId(ROUTE_ID).ticketPrice(BigDecimal.ONE).build();
            when(dimRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.of(existing));

            dimensionService.applyRoute(EVENT_ID, EventTypes.ROUTE_UPDATED, new RouteEventPayload(ROUTE_ID, BRANCH_ID,
                    DESTINATION_ID, new BigDecimal("120.50"), new BigDecimal("60.00"), false));

            verify(dimRouteRepository).save(existing);
            assertEquals(new BigDecimal("60.00"), existing.getTicketPrice());
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            dimensionService.applyRoute(EVENT_ID, EventTypes.ROUTE_DELETED, new RouteEventPayload(ROUTE_ID, BRANCH_ID,
                    DESTINATION_ID, null, null, true));

            verifyNoInteractions(dimRouteRepository);
            verify(processedEventRepository, never()).save(any());
        }
    }

    private static BranchEventPayload branchPayload(String name, boolean active) {
        return new BranchEventPayload(BRANCH_ID, name, "CEN", new BigDecimal("14.634900"),
                new BigDecimal("-90.506900"), active);
    }

    private static BusEventPayload busPayload(UUID branchId, String status) {
        return new BusEventPayload(BUS_ID, branchId, "P-123ABC", "Volvo", "B8R", (short) 2020, 40, status,
                new BigDecimal("1500.00"));
    }

    private static DriverEventPayload driverPayload(boolean active) {
        return new DriverEventPayload(DRIVER_ID, USER_ID, BRANCH_ID, "Juan Pérez", "A-123", "A", EXPIRES_ON, active);
    }

    private void assertMarkedProcessed(String eventType) {
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
        assertEquals(eventType, processedEventCaptor.getValue().getEventType());
    }
}
