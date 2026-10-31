package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.FactTicketSaleEntity;
import com.urbantransport.reporting.domain.FactTripEntity;
import com.urbantransport.reporting.domain.LedgerConcept;
import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerEntryType;
import com.urbantransport.reporting.domain.LedgerSourceType;
import com.urbantransport.reporting.domain.ProcessedEventEntity;
import com.urbantransport.reporting.event.EventTypes;
import com.urbantransport.reporting.event.TicketCancelledPayload;
import com.urbantransport.reporting.event.TicketPurchasedPayload;
import com.urbantransport.reporting.repository.FactTicketSaleRepository;
import com.urbantransport.reporting.repository.FactTripRepository;
import com.urbantransport.reporting.repository.ProcessedEventRepository;
import com.urbantransport.reporting.service.LedgerService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketFactServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TRIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TICKET_1 = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final UUID TICKET_2 = UUID.fromString("44444444-4444-4444-4444-444444444442");
    private static final UUID BRANCH_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID ORIGIN_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID ROUTE_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID BUS_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final LocalDate TODAY = LocalDate.now(ZONE);
    private static final LocalDate PAYMENT_DATE = TODAY.minusDays(3);
    private static final Instant OCCURRED_AT = TODAY.minusDays(1).atTime(23, 0).atZone(ZONE).toInstant();

    @Mock
    private FactTicketSaleRepository factTicketSaleRepository;

    @Mock
    private FactTripRepository factTripRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private LedgerService ledgerService;

    @Captor
    private ArgumentCaptor<List<FactTicketSaleEntity>> salesCaptor;

    @Captor
    private ArgumentCaptor<FactTicketSaleEntity> saleCaptor;

    @Captor
    private ArgumentCaptor<LedgerEntryEntity> entryCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEventEntity> processedEventCaptor;

    private TicketFactServiceImpl ticketFactService;

    @BeforeEach
    void setUp() {
        ticketFactService = new TicketFactServiceImpl(factTicketSaleRepository, factTripRepository,
                processedEventRepository, ledgerService);
        ReflectionTestUtils.setField(ticketFactService, "zone", ZONE);
    }

    @Nested
    class ApplyTicketPurchased {

        @Test
        void onlineOrderWithKnownTrip_savesSalesWithRouteAndBusAndRecordsIncome() {
            when(factTripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip()));
            when(factTicketSaleRepository.findById(TICKET_1)).thenReturn(Optional.empty());
            when(factTicketSaleRepository.findById(TICKET_2)).thenReturn(Optional.empty());

            ticketFactService.applyTicketPurchased(EVENT_ID, EventTypes.TICKET_PURCHASED, OCCURRED_AT,
                    purchased("ONLINE", BRANCH_ID, PAYMENT_DATE, tripInfo(),
                            ticket(TICKET_1, 1, "50.00"), ticket(TICKET_2, 2, "50.00")));

            verify(factTicketSaleRepository).saveAll(salesCaptor.capture());
            List<FactTicketSaleEntity> sales = salesCaptor.getValue();
            assertEquals(2, sales.size());
            FactTicketSaleEntity first = sales.getFirst();
            assertEquals(TICKET_1, first.getTicketId());
            assertEquals(ORDER_ID, first.getOrderId());
            assertEquals(TRIP_ID, first.getTripId());
            assertEquals(ROUTE_ID, first.getRouteId());
            assertEquals(BUS_ID, first.getBusId());
            assertEquals(BRANCH_ID, first.getBranchId());
            assertEquals("ONLINE", first.getChannel());
            assertEquals(1, first.getSeatNumber());
            assertEquals(new BigDecimal("50.00"), first.getPricePaid());
            assertEquals(PAYMENT_DATE, first.getPaymentDate());
            assertFalse(first.isCancelled());

            verify(ledgerService).record(entryCaptor.capture());
            LedgerEntryEntity entry = entryCaptor.getValue();
            assertEquals(LedgerEntryType.INCOME, entry.getType());
            assertEquals(LedgerConcept.TICKET_SALE, entry.getConcept());
            assertEquals(LedgerSourceType.TICKET_SALE, entry.getSourceType());
            assertEquals(ORDER_ID, entry.getSourceId());
            assertEquals(BRANCH_ID, entry.getBranchId());
            assertEquals(new BigDecimal("100.00"), entry.getAmount());
            assertEquals(PAYMENT_DATE, entry.getOccurredOn());
            assertEquals("Venta en línea: Xela - Guatemala (2 boletos)", entry.getDescription());
            assertMarkedProcessed(EventTypes.TICKET_PURCHASED);
        }

        @Test
        void orderWithoutBranch_usesTripOriginBranch() {
            when(factTripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip()));
            when(factTicketSaleRepository.findById(TICKET_1)).thenReturn(Optional.empty());

            ticketFactService.applyTicketPurchased(EVENT_ID, EventTypes.TICKET_PURCHASED, OCCURRED_AT,
                    purchased("COUNTER", null, PAYMENT_DATE, tripInfo(), ticket(TICKET_1, 1, "50.00")));

            verify(factTicketSaleRepository).saveAll(salesCaptor.capture());
            assertEquals(ORIGIN_ID, salesCaptor.getValue().getFirst().getBranchId());
            verify(ledgerService).record(entryCaptor.capture());
            assertEquals(ORIGIN_ID, entryCaptor.getValue().getBranchId());
            assertEquals("Venta en mostrador: Xela - Guatemala (1 boleto)", entryCaptor.getValue().getDescription());
        }

        @Test
        void tripNotYetReceived_savesSalesWithoutRouteAndBus() {
            when(factTripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
            when(factTicketSaleRepository.findById(TICKET_1)).thenReturn(Optional.empty());

            ticketFactService.applyTicketPurchased(EVENT_ID, EventTypes.TICKET_PURCHASED, OCCURRED_AT,
                    purchased("ONLINE", null, PAYMENT_DATE, tripInfo(), ticket(TICKET_1, 1, "50.00")));

            verify(factTicketSaleRepository).saveAll(salesCaptor.capture());
            FactTicketSaleEntity sale = salesCaptor.getValue().getFirst();
            assertEquals(TRIP_ID, sale.getTripId());
            assertNull(sale.getRouteId());
            assertNull(sale.getBusId());
            assertNull(sale.getBranchId());
        }

        @Test
        void orderWithoutTripInfoOrPaymentDate_usesOccurredAtAndPlainDescription() {
            when(factTicketSaleRepository.findById(TICKET_1)).thenReturn(Optional.empty());

            ticketFactService.applyTicketPurchased(EVENT_ID, EventTypes.TICKET_PURCHASED, OCCURRED_AT,
                    purchased("COUNTER", BRANCH_ID, null, null, ticket(TICKET_1, 1, "75.00")));

            verifyNoInteractions(factTripRepository);
            verify(factTicketSaleRepository).saveAll(salesCaptor.capture());
            assertNull(salesCaptor.getValue().getFirst().getTripId());
            verify(ledgerService).record(entryCaptor.capture());
            assertEquals(TODAY.minusDays(1), entryCaptor.getValue().getOccurredOn());
            assertEquals("Venta en mostrador (1 boleto)", entryCaptor.getValue().getDescription());
        }

        @Test
        void tripInfoWithoutNames_keepsPlainDescription() {
            when(factTripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
            when(factTicketSaleRepository.findById(TICKET_1)).thenReturn(Optional.empty());

            ticketFactService.applyTicketPurchased(EVENT_ID, EventTypes.TICKET_PURCHASED, null,
                    purchased("ONLINE", BRANCH_ID, null, new TicketPurchasedPayload.TripInfo(TRIP_ID, null, null),
                            ticket(TICKET_1, 1, "75.00")));

            verify(ledgerService).record(entryCaptor.capture());
            assertEquals(TODAY, entryCaptor.getValue().getOccurredOn());
            assertEquals("Venta en línea (1 boleto)", entryCaptor.getValue().getDescription());
        }

        @Test
        void ticketWithoutPrice_isNotAddedToIncome() {
            when(factTicketSaleRepository.findById(TICKET_1)).thenReturn(Optional.empty());
            when(factTicketSaleRepository.findById(TICKET_2)).thenReturn(Optional.empty());

            ticketFactService.applyTicketPurchased(EVENT_ID, EventTypes.TICKET_PURCHASED, OCCURRED_AT,
                    purchased("COUNTER", BRANCH_ID, PAYMENT_DATE, null, ticket(TICKET_1, 1, "75.00"),
                            ticket(TICKET_2, 2, null)));

            verify(ledgerService).record(entryCaptor.capture());
            assertEquals(new BigDecimal("75.00"), entryCaptor.getValue().getAmount());
        }

        @Test
        void orderWithoutTickets_savesNothingAndRecordsZero() {
            ticketFactService.applyTicketPurchased(EVENT_ID, EventTypes.TICKET_PURCHASED, OCCURRED_AT,
                    new TicketPurchasedPayload(ORDER_ID, "ONLINE", BRANCH_ID, PAYMENT_DATE, BigDecimal.ZERO, null,
                            null));

            verify(factTicketSaleRepository).saveAll(salesCaptor.capture());
            assertTrue(salesCaptor.getValue().isEmpty());
            verify(ledgerService).record(entryCaptor.capture());
            assertEquals(0, BigDecimal.ZERO.compareTo(entryCaptor.getValue().getAmount()));
            assertEquals("Venta en línea (0 boletos)", entryCaptor.getValue().getDescription());
        }

        @Test
        void ticketCancelledBeforePurchaseArrived_staysCancelled() {
            FactTicketSaleEntity cancelled = FactTicketSaleEntity.builder()
                    .ticketId(TICKET_1)
                    .cancelled(true)
                    .cancelledAt(OCCURRED_AT)
                    .build();
            when(factTicketSaleRepository.findById(TICKET_1)).thenReturn(Optional.of(cancelled));

            ticketFactService.applyTicketPurchased(EVENT_ID, EventTypes.TICKET_PURCHASED, OCCURRED_AT,
                    purchased("COUNTER", BRANCH_ID, PAYMENT_DATE, null, ticket(TICKET_1, 1, "75.00")));

            verify(factTicketSaleRepository).saveAll(salesCaptor.capture());
            FactTicketSaleEntity sale = salesCaptor.getValue().getFirst();
            assertSame(cancelled, sale);
            assertTrue(sale.isCancelled());
            assertEquals(OCCURRED_AT, sale.getCancelledAt());
            assertEquals(new BigDecimal("75.00"), sale.getPricePaid());
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            ticketFactService.applyTicketPurchased(EVENT_ID, EventTypes.TICKET_PURCHASED, OCCURRED_AT,
                    purchased("ONLINE", BRANCH_ID, PAYMENT_DATE, tripInfo(), ticket(TICKET_1, 1, "50.00")));

            verifyNoInteractions(factTicketSaleRepository, factTripRepository, ledgerService);
            verify(processedEventRepository, never()).save(any());
        }
    }

    @Nested
    class ApplyTicketCancelled {

        @Test
        void knownSale_isMarkedCancelledAndRecordsNegativeIncome() {
            FactTicketSaleEntity sale = FactTicketSaleEntity.builder()
                    .ticketId(TICKET_1)
                    .branchId(BRANCH_ID)
                    .pricePaid(new BigDecimal("50.00"))
                    .build();
            when(factTicketSaleRepository.findById(TICKET_1)).thenReturn(Optional.of(sale));

            ticketFactService.applyTicketCancelled(EVENT_ID, EventTypes.TICKET_CANCELLED, OCCURRED_AT,
                    cancelled(BRANCH_ID, "50.00", "1A", "WALLET"));

            verify(factTicketSaleRepository).save(saleCaptor.capture());
            assertSame(sale, saleCaptor.getValue());
            assertTrue(sale.isCancelled());
            assertEquals(OCCURRED_AT, sale.getCancelledAt());

            verify(ledgerService).record(entryCaptor.capture());
            LedgerEntryEntity entry = entryCaptor.getValue();
            assertEquals(LedgerEntryType.INCOME, entry.getType());
            assertEquals(LedgerConcept.TICKET_SALE, entry.getConcept());
            assertEquals(LedgerSourceType.TICKET_CANCELLATION, entry.getSourceType());
            assertEquals(TICKET_1, entry.getSourceId());
            assertEquals(BRANCH_ID, entry.getBranchId());
            assertEquals(new BigDecimal("-50.00"), entry.getAmount());
            assertEquals(TODAY.minusDays(1), entry.getOccurredOn());
            assertEquals("Cancelación del boleto, asiento 1A (reembolso a cartera)", entry.getDescription());
            assertMarkedProcessed(EventTypes.TICKET_CANCELLED);
        }

        @Test
        void unknownSale_isCreatedFromPayloadAsCancelled() {
            when(factTicketSaleRepository.findById(TICKET_1)).thenReturn(Optional.empty());

            ticketFactService.applyTicketCancelled(EVENT_ID, EventTypes.TICKET_CANCELLED, OCCURRED_AT,
                    cancelled(BRANCH_ID, "50.00", null, "CASH"));

            verify(factTicketSaleRepository).save(saleCaptor.capture());
            FactTicketSaleEntity sale = saleCaptor.getValue();
            assertEquals(TICKET_1, sale.getTicketId());
            assertEquals(ORDER_ID, sale.getOrderId());
            assertEquals(TRIP_ID, sale.getTripId());
            assertEquals(BRANCH_ID, sale.getBranchId());
            assertEquals("COUNTER", sale.getChannel());
            assertEquals(4, sale.getSeatNumber());
            assertEquals(new BigDecimal("50.00"), sale.getPricePaid());
            assertTrue(sale.isCancelled());
            verify(ledgerService).record(entryCaptor.capture());
            assertEquals("Cancelación del boleto, asiento 4 (reembolso en efectivo)",
                    entryCaptor.getValue().getDescription());
        }

        @Test
        void payloadWithoutPriceOrBranch_usesStoredSale() {
            FactTicketSaleEntity sale = FactTicketSaleEntity.builder()
                    .ticketId(TICKET_1)
                    .branchId(ORIGIN_ID)
                    .pricePaid(new BigDecimal("65.00"))
                    .build();
            when(factTicketSaleRepository.findById(TICKET_1)).thenReturn(Optional.of(sale));

            ticketFactService.applyTicketCancelled(EVENT_ID, EventTypes.TICKET_CANCELLED, null,
                    cancelled(null, null, "2B", "WALLET"));

            assertNotNull(sale.getCancelledAt());
            verify(ledgerService).record(entryCaptor.capture());
            assertEquals(ORIGIN_ID, entryCaptor.getValue().getBranchId());
            assertEquals(new BigDecimal("-65.00"), entryCaptor.getValue().getAmount());
            assertEquals(TODAY, entryCaptor.getValue().getOccurredOn());
        }

        @Test
        void withoutAnyPrice_recordsNullAmountForLedgerToSkip() {
            when(factTicketSaleRepository.findById(TICKET_1)).thenReturn(Optional.empty());

            ticketFactService.applyTicketCancelled(EVENT_ID, EventTypes.TICKET_CANCELLED, OCCURRED_AT,
                    cancelled(BRANCH_ID, null, "2B", "WALLET"));

            verify(ledgerService).record(entryCaptor.capture());
            assertNull(entryCaptor.getValue().getAmount());
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            ticketFactService.applyTicketCancelled(EVENT_ID, EventTypes.TICKET_CANCELLED, OCCURRED_AT,
                    cancelled(BRANCH_ID, "50.00", "1A", "WALLET"));

            verifyNoInteractions(factTicketSaleRepository, ledgerService);
            verify(processedEventRepository, never()).save(any());
        }
    }

    private static FactTripEntity trip() {
        return FactTripEntity.builder()
                .tripId(TRIP_ID)
                .routeId(ROUTE_ID)
                .busId(BUS_ID)
                .originBranchId(ORIGIN_ID)
                .build();
    }

    private static TicketPurchasedPayload.TripInfo tripInfo() {
        return new TicketPurchasedPayload.TripInfo(TRIP_ID, "Xela", "Guatemala");
    }

    private static TicketPurchasedPayload.TicketInfo ticket(UUID ticketId, int seat, String price) {
        return new TicketPurchasedPayload.TicketInfo(ticketId, seat, "1" + seat,
                price == null ? null : new BigDecimal(price));
    }

    private static TicketPurchasedPayload purchased(String channel, UUID branchId, LocalDate paymentDate,
                                                    TicketPurchasedPayload.TripInfo trip,
                                                    TicketPurchasedPayload.TicketInfo... tickets) {
        return new TicketPurchasedPayload(ORDER_ID, channel, branchId, paymentDate, null, trip,
                new ArrayList<>(Arrays.asList(tickets)));
    }

    private static TicketCancelledPayload cancelled(UUID branchId, String price, String seatLabel,
                                                    String refundMethod) {
        return new TicketCancelledPayload(TICKET_1, ORDER_ID, TRIP_ID, 4, seatLabel, "COUNTER", branchId,
                price == null ? null : new BigDecimal(price), refundMethod);
    }

    private void assertMarkedProcessed(String eventType) {
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
        assertEquals(eventType, processedEventCaptor.getValue().getEventType());
    }
}
