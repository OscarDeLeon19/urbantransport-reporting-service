package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.DimBusEntity;
import com.urbantransport.reporting.domain.FactTripEntity;
import com.urbantransport.reporting.domain.LedgerConcept;
import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerEntryType;
import com.urbantransport.reporting.domain.LedgerSourceType;
import com.urbantransport.reporting.domain.ProcessedEventEntity;
import com.urbantransport.reporting.event.EventTypes;
import com.urbantransport.reporting.event.TripEventPayload;
import com.urbantransport.reporting.repository.DimBusRepository;
import com.urbantransport.reporting.repository.FactTicketSaleRepository;
import com.urbantransport.reporting.repository.FactTripRepository;
import com.urbantransport.reporting.repository.ProcessedEventRepository;
import com.urbantransport.reporting.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripFactServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRIP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ROUTE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID BUS_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID DRIVER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID ORIGIN_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID DESTINATION_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID BUS_BRANCH_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID RENTAL_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final LocalDate TODAY = LocalDate.now(ZONE);
    private static final LocalDateTime DEPARTURE = TODAY.plusDays(2).atTime(8, 30);
    private static final Instant DEPARTED_AT = TODAY.atTime(6, 0).atZone(ZONE).toInstant();
    private static final Instant ARRIVED_AT = TODAY.minusDays(1).atTime(23, 30).atZone(ZONE).toInstant();

    @Mock
    private FactTripRepository factTripRepository;

    @Mock
    private FactTicketSaleRepository factTicketSaleRepository;

    @Mock
    private DimBusRepository dimBusRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private LedgerService ledgerService;

    @Captor
    private ArgumentCaptor<FactTripEntity> tripCaptor;

    @Captor
    private ArgumentCaptor<LedgerEntryEntity> entryCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEventEntity> processedEventCaptor;

    private TripFactServiceImpl tripFactService;

    @BeforeEach
    void setUp() {
        tripFactService = new TripFactServiceImpl(factTripRepository, factTicketSaleRepository, dimBusRepository,
                processedEventRepository, ledgerService);
        ReflectionTestUtils.setField(tripFactService, "zone", ZONE);
    }

    @Test
    void scheduledTrip_isInsertedWithLocalDepartureConvertedAndSalesSynced() {
        when(factTripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());

        tripFactService.applyTripEvent(EVENT_ID, EventTypes.TRIP_SCHEDULED, scheduled());

        verify(factTripRepository).save(tripCaptor.capture());
        FactTripEntity saved = tripCaptor.getValue();
        assertEquals(TRIP_ID, saved.getTripId());
        assertEquals("REGULAR", saved.getType());
        assertEquals(ROUTE_ID, saved.getRouteId());
        assertEquals(ORIGIN_ID, saved.getOriginBranchId());
        assertEquals(DESTINATION_ID, saved.getDestinationBranchId());
        assertEquals(BUS_ID, saved.getBusId());
        assertEquals(DRIVER_ID, saved.getDriverId());
        assertEquals(new BigDecimal("50.00"), saved.getTicketPrice());
        assertEquals(DEPARTURE.atZone(ZONE).toInstant(), saved.getScheduledDeparture());
        assertEquals("SCHEDULED", saved.getStatus());
        assertNull(saved.getActualArrivalAt());
        verify(factTicketSaleRepository).syncTrip(TRIP_ID, ROUTE_ID, BUS_ID);
        verifyNoInteractions(ledgerService, dimBusRepository);
        assertMarkedProcessed(EventTypes.TRIP_SCHEDULED);
    }

    @Test
    void laterEvent_keepsFieldsItDoesNotCarry() {
        FactTripEntity existing = FactTripEntity.builder()
                .tripId(TRIP_ID)
                .type("REGULAR")
                .routeId(ROUTE_ID)
                .busId(BUS_ID)
                .ticketPrice(new BigDecimal("50.00"))
                .scheduledDeparture(DEPARTURE.atZone(ZONE).toInstant())
                .status("SCHEDULED")
                .build();
        when(factTripRepository.findById(TRIP_ID)).thenReturn(Optional.of(existing));

        tripFactService.applyTripEvent(EVENT_ID, EventTypes.TRIP_STARTED, new TripEventPayload(TRIP_ID, null, null,
                null, null, null, null, DRIVER_ID, null, null, DEPARTED_AT, null, null, null, null, "IN_TRANSIT"));

        verify(factTripRepository).save(tripCaptor.capture());
        assertSame(existing, tripCaptor.getValue());
        assertEquals("REGULAR", existing.getType());
        assertEquals(ROUTE_ID, existing.getRouteId());
        assertEquals(new BigDecimal("50.00"), existing.getTicketPrice());
        assertEquals(DEPARTURE.atZone(ZONE).toInstant(), existing.getScheduledDeparture());
        assertEquals(DRIVER_ID, existing.getDriverId());
        assertEquals(DEPARTED_AT, existing.getActualDepartureAt());
        assertEquals("IN_TRANSIT", existing.getStatus());
        verify(factTicketSaleRepository).syncTrip(TRIP_ID, ROUTE_ID, BUS_ID);
        verifyNoInteractions(ledgerService);
    }

    @Test
    void completedTripWithKnownBus_recordsFuelAndDriverPayInBusBranch() {
        when(factTripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
        when(dimBusRepository.findById(BUS_ID)).thenReturn(Optional.of(bus("P-123ABC")));

        tripFactService.applyTripEvent(EVENT_ID, EventTypes.TRIP_COMPLETED, completed("REGULAR", BUS_ID, ARRIVED_AT,
                new BigDecimal("120.50")));

        verify(factTripRepository).save(tripCaptor.capture());
        FactTripEntity saved = tripCaptor.getValue();
        assertEquals(RENTAL_ID, saved.getRentalId());
        assertEquals(ARRIVED_AT, saved.getActualArrivalAt());
        assertEquals(new BigDecimal("120.50"), saved.getKmTraveled());
        assertEquals(new BigDecimal("300.00"), saved.getFuelCost());
        assertEquals(new BigDecimal("150.00"), saved.getDriverPay());
        assertEquals("COMPLETED", saved.getStatus());

        List<LedgerEntryEntity> entries = recordedEntries();
        LedgerEntryEntity fuel = entries.get(0);
        assertEquals(LedgerEntryType.EXPENSE, fuel.getType());
        assertEquals(LedgerConcept.FUEL, fuel.getConcept());
        assertEquals(LedgerSourceType.TRIP_FUEL, fuel.getSourceType());
        assertEquals(TRIP_ID, fuel.getSourceId());
        assertEquals(BUS_BRANCH_ID, fuel.getBranchId());
        assertEquals(new BigDecimal("300.00"), fuel.getAmount());
        assertEquals(TODAY.minusDays(1), fuel.getOccurredOn());
        assertEquals("Combustible del viaje regular del bus P-123ABC (120.5 km)", fuel.getDescription());
        LedgerEntryEntity pay = entries.get(1);
        assertEquals(LedgerConcept.DRIVER_PAY, pay.getConcept());
        assertEquals(LedgerSourceType.TRIP_DRIVER_PAY, pay.getSourceType());
        assertEquals(BUS_BRANCH_ID, pay.getBranchId());
        assertEquals(new BigDecimal("150.00"), pay.getAmount());
        assertEquals("Pago del chofer del viaje regular del bus P-123ABC (120.5 km)", pay.getDescription());
        assertMarkedProcessed(EventTypes.TRIP_COMPLETED);
    }

    @Test
    void completedTripWithUnknownBus_recordsWithoutBranchToCompleteLater() {
        when(factTripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
        when(dimBusRepository.findById(BUS_ID)).thenReturn(Optional.empty());

        tripFactService.applyTripEvent(EVENT_ID, EventTypes.TRIP_COMPLETED, completed("PRIVATE", BUS_ID, ARRIVED_AT,
                new BigDecimal("415.00")));

        List<LedgerEntryEntity> entries = recordedEntries();
        assertNull(entries.get(0).getBranchId());
        assertNull(entries.get(1).getBranchId());
        assertEquals("Combustible del viaje privado (415 km)", entries.get(0).getDescription());
    }

    @Test
    void completedTripWithBusWithoutPlate_omitsPlate() {
        when(factTripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
        when(dimBusRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(null)));

        tripFactService.applyTripEvent(EVENT_ID, EventTypes.TRIP_COMPLETED, completed("REGULAR", BUS_ID, ARRIVED_AT,
                null));

        List<LedgerEntryEntity> entries = recordedEntries();
        assertEquals(BUS_BRANCH_ID, entries.get(0).getBranchId());
        assertEquals("Combustible del viaje regular", entries.get(0).getDescription());
    }

    @Test
    void completedTripWithoutBusOrArrival_recordsTodayWithoutBranch() {
        when(factTripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());

        tripFactService.applyTripEvent(EVENT_ID, EventTypes.TRIP_COMPLETED, completed("REGULAR", null, null,
                new BigDecimal("10.00")));

        verifyNoInteractions(dimBusRepository);
        List<LedgerEntryEntity> entries = recordedEntries();
        assertNull(entries.get(0).getBranchId());
        assertEquals(TODAY, entries.get(0).getOccurredOn());
        assertEquals(TODAY, entries.get(1).getOccurredOn());
    }

    @ParameterizedTest
    @ValueSource(strings = {EventTypes.TRIP_UPDATED, EventTypes.TRIP_STARTED, EventTypes.TRIP_DELETED})
    void nonCompletedEvent_doesNotTouchLedger(String eventType) {
        when(factTripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());

        tripFactService.applyTripEvent(EVENT_ID, eventType, completed("REGULAR", BUS_ID, ARRIVED_AT,
                new BigDecimal("10.00")));

        verify(factTripRepository).save(any(FactTripEntity.class));
        verifyNoInteractions(ledgerService, dimBusRepository);
        assertMarkedProcessed(eventType);
    }

    @Test
    void alreadyProcessedEvent_isIgnored() {
        when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

        tripFactService.applyTripEvent(EVENT_ID, EventTypes.TRIP_COMPLETED, completed("REGULAR", BUS_ID, ARRIVED_AT,
                new BigDecimal("10.00")));

        verifyNoInteractions(factTripRepository, factTicketSaleRepository, dimBusRepository, ledgerService);
        verify(processedEventRepository, never()).save(any());
    }

    private static TripEventPayload scheduled() {
        return new TripEventPayload(TRIP_ID, "REGULAR", ROUTE_ID, null, ORIGIN_ID, DESTINATION_ID, BUS_ID, DRIVER_ID,
                new BigDecimal("50.00"), DEPARTURE, null, null, null, null, null, "SCHEDULED");
    }

    private static TripEventPayload completed(String type, UUID busId, Instant arrivedAt, BigDecimal km) {
        return new TripEventPayload(TRIP_ID, type, ROUTE_ID, RENTAL_ID, ORIGIN_ID, DESTINATION_ID, busId, DRIVER_ID,
                null, null, DEPARTED_AT, arrivedAt, km, new BigDecimal("300.00"), new BigDecimal("150.00"),
                "COMPLETED");
    }

    private static DimBusEntity bus(String plate) {
        return DimBusEntity.builder().busId(BUS_ID).branchId(BUS_BRANCH_ID).plate(plate).build();
    }

    private List<LedgerEntryEntity> recordedEntries() {
        verify(ledgerService, times(2)).record(entryCaptor.capture());
        return entryCaptor.getAllValues();
    }

    private void assertMarkedProcessed(String eventType) {
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
        assertEquals(eventType, processedEventCaptor.getValue().getEventType());
    }
}
