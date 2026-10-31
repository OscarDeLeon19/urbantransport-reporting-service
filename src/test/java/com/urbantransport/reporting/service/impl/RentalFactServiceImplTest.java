package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.FactRentalEntity;
import com.urbantransport.reporting.domain.LedgerConcept;
import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerEntryType;
import com.urbantransport.reporting.domain.LedgerSourceType;
import com.urbantransport.reporting.domain.ProcessedEventEntity;
import com.urbantransport.reporting.event.EventTypes;
import com.urbantransport.reporting.event.RentalAssignedPayload;
import com.urbantransport.reporting.event.RentalEventPayload;
import com.urbantransport.reporting.repository.FactRentalRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RentalFactServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RENTAL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BRANCH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID BUS_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final LocalDate TODAY = LocalDate.now(ZONE);
    private static final LocalDate PAYMENT_DATE = TODAY.minusDays(2);
    private static final Instant OCCURRED_AT = TODAY.minusDays(1).atTime(22, 0).atZone(ZONE).toInstant();

    @Mock
    private FactRentalRepository factRentalRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private LedgerService ledgerService;

    @Captor
    private ArgumentCaptor<FactRentalEntity> rentalCaptor;

    @Captor
    private ArgumentCaptor<LedgerEntryEntity> entryCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEventEntity> processedEventCaptor;

    private RentalFactServiceImpl rentalFactService;

    @BeforeEach
    void setUp() {
        rentalFactService = new RentalFactServiceImpl(factRentalRepository, processedEventRepository, ledgerService);
        ReflectionTestUtils.setField(rentalFactService, "zone", ZONE);
    }

    @Nested
    class ApplyRentalPaid {

        @Test
        void newRental_isInsertedAndRecordsIncome() {
            when(factRentalRepository.findById(RENTAL_ID)).thenReturn(Optional.empty());

            rentalFactService.applyRentalPaid(EVENT_ID, EventTypes.RENTAL_PAID, OCCURRED_AT, paid(PAYMENT_DATE));

            verify(factRentalRepository).save(rentalCaptor.capture());
            FactRentalEntity saved = rentalCaptor.getValue();
            assertEquals(RENTAL_ID, saved.getRentalId());
            assertEquals(BRANCH_ID, saved.getBranchId());
            assertEquals("ONLINE", saved.getChannel());
            assertEquals("1234567", saved.getCustomerNit());
            assertEquals("Xela", saved.getOriginText());
            assertEquals("Tikal", saved.getDestinationText());
            assertEquals(new BigDecimal("1000.00"), saved.getAmount());
            assertEquals(PAYMENT_DATE, saved.getPaymentDate());
            assertEquals(TODAY.plusDays(5), saved.getDepartureDate());
            assertEquals(TODAY.plusDays(6), saved.getReturnDate());
            assertEquals(30, saved.getPassengers());
            assertEquals(new BigDecimal("500.00"), saved.getDistanceKm());
            assertEquals("PAID", saved.getStatus());

            verify(ledgerService).record(entryCaptor.capture());
            LedgerEntryEntity entry = entryCaptor.getValue();
            assertEquals(LedgerEntryType.INCOME, entry.getType());
            assertEquals(LedgerConcept.RENTAL, entry.getConcept());
            assertEquals(LedgerSourceType.RENTAL, entry.getSourceType());
            assertEquals(RENTAL_ID, entry.getSourceId());
            assertEquals(BRANCH_ID, entry.getBranchId());
            assertEquals(new BigDecimal("1000.00"), entry.getAmount());
            assertEquals(PAYMENT_DATE, entry.getOccurredOn());
            assertEquals("Alquiler NIT 1234567: Xela - Tikal", entry.getDescription());
            assertMarkedProcessed(EventTypes.RENTAL_PAID);
        }

        @Test
        void alreadyAssignedRental_keepsItsStatusAndBus() {
            FactRentalEntity existing = FactRentalEntity.builder()
                    .rentalId(RENTAL_ID)
                    .busId(BUS_ID)
                    .status("ASSIGNED")
                    .build();
            when(factRentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(existing));

            rentalFactService.applyRentalPaid(EVENT_ID, EventTypes.RENTAL_PAID, OCCURRED_AT, paid(PAYMENT_DATE));

            verify(factRentalRepository).save(rentalCaptor.capture());
            assertSame(existing, rentalCaptor.getValue());
            assertEquals("ASSIGNED", existing.getStatus());
            assertEquals(BUS_ID, existing.getBusId());
            assertEquals(new BigDecimal("1000.00"), existing.getAmount());
        }

        @Test
        void withoutPaymentDate_usesOccurredAtDate() {
            when(factRentalRepository.findById(RENTAL_ID)).thenReturn(Optional.empty());

            rentalFactService.applyRentalPaid(EVENT_ID, EventTypes.RENTAL_PAID, OCCURRED_AT, paid(null));

            verify(ledgerService).record(entryCaptor.capture());
            assertEquals(TODAY.minusDays(1), entryCaptor.getValue().getOccurredOn());
        }

        @Test
        void withoutPaymentDateOrOccurredAt_usesToday() {
            when(factRentalRepository.findById(RENTAL_ID)).thenReturn(Optional.empty());

            rentalFactService.applyRentalPaid(EVENT_ID, EventTypes.RENTAL_PAID, null, paid(null));

            verify(ledgerService).record(entryCaptor.capture());
            assertEquals(TODAY, entryCaptor.getValue().getOccurredOn());
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            rentalFactService.applyRentalPaid(EVENT_ID, EventTypes.RENTAL_PAID, OCCURRED_AT, paid(PAYMENT_DATE));

            verifyNoInteractions(factRentalRepository, ledgerService);
            verify(processedEventRepository, never()).save(any());
        }
    }

    @Nested
    class ApplyRentalAssigned {

        @Test
        void paidRental_getsBusAndAssignedStatus() {
            FactRentalEntity existing = FactRentalEntity.builder()
                    .rentalId(RENTAL_ID)
                    .branchId(BRANCH_ID)
                    .status("PAID")
                    .build();
            when(factRentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(existing));

            rentalFactService.applyRentalAssigned(EVENT_ID, EventTypes.RENTAL_ASSIGNED,
                    new RentalAssignedPayload(RENTAL_ID, BRANCH_ID, BUS_ID));

            verify(factRentalRepository).save(rentalCaptor.capture());
            assertSame(existing, rentalCaptor.getValue());
            assertEquals(BUS_ID, existing.getBusId());
            assertEquals("ASSIGNED", existing.getStatus());
            verifyNoInteractions(ledgerService);
            assertMarkedProcessed(EventTypes.RENTAL_ASSIGNED);
        }

        @Test
        void unknownRental_isCreatedWithBranchAndBus() {
            when(factRentalRepository.findById(RENTAL_ID)).thenReturn(Optional.empty());

            rentalFactService.applyRentalAssigned(EVENT_ID, EventTypes.RENTAL_ASSIGNED,
                    new RentalAssignedPayload(RENTAL_ID, BRANCH_ID, BUS_ID));

            verify(factRentalRepository).save(rentalCaptor.capture());
            FactRentalEntity saved = rentalCaptor.getValue();
            assertEquals(RENTAL_ID, saved.getRentalId());
            assertEquals(BRANCH_ID, saved.getBranchId());
            assertEquals(BUS_ID, saved.getBusId());
            assertEquals("ASSIGNED", saved.getStatus());
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            rentalFactService.applyRentalAssigned(EVENT_ID, EventTypes.RENTAL_ASSIGNED,
                    new RentalAssignedPayload(RENTAL_ID, BRANCH_ID, BUS_ID));

            verifyNoInteractions(factRentalRepository, ledgerService);
            verify(processedEventRepository, never()).save(any());
        }
    }

    private static RentalEventPayload paid(LocalDate paymentDate) {
        return new RentalEventPayload(RENTAL_ID, "ONLINE", BRANCH_ID, "1234567", "Xela", "Tikal",
                TODAY.plusDays(5), TODAY.plusDays(6), 30, new BigDecimal("500.00"), new BigDecimal("1000.00"),
                paymentDate, "PAID");
    }

    private void assertMarkedProcessed(String eventType) {
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
        assertEquals(eventType, processedEventCaptor.getValue().getEventType());
    }
}
