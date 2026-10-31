package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.LedgerConcept;
import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerEntryType;
import com.urbantransport.reporting.domain.LedgerSourceType;
import com.urbantransport.reporting.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceImplTest {

    private static final UUID SOURCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Guatemala"));

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Captor
    private ArgumentCaptor<LedgerEntryEntity> entryCaptor;

    private LedgerServiceImpl ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerServiceImpl(ledgerEntryRepository);
    }

    @Nested
    class Record {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"0", "0.00"})
        void entryWithoutAmount_isSkipped(String amount) {
            ledgerService.record(entry(amount == null ? null : new BigDecimal(amount), BRANCH_ID, "Combustible"));

            verifyNoInteractions(ledgerEntryRepository);
        }

        @Test
        void alreadyRecordedSource_isIgnored() {
            when(ledgerEntryRepository.existsBySourceTypeAndSourceId(LedgerSourceType.TRIP_FUEL, SOURCE_ID))
                    .thenReturn(true);

            ledgerService.record(entry(new BigDecimal("200"), BRANCH_ID, "Combustible"));

            verify(ledgerEntryRepository, never()).save(any());
        }

        @Test
        void newEntry_isSavedWithAmountRoundedToCents() {
            ledgerService.record(entry(new BigDecimal("200.555"), BRANCH_ID, "Combustible"));

            verify(ledgerEntryRepository).save(entryCaptor.capture());
            LedgerEntryEntity saved = entryCaptor.getValue();
            assertEquals(new BigDecimal("200.56"), saved.getAmount());
            assertEquals(BRANCH_ID, saved.getBranchId());
            assertEquals(LedgerSourceType.TRIP_FUEL, saved.getSourceType());
            assertEquals("Combustible", saved.getDescription());
        }

        @Test
        void negativeAmount_isSaved() {
            ledgerService.record(entry(new BigDecimal("-50"), BRANCH_ID, "Cancelación"));

            verify(ledgerEntryRepository).save(entryCaptor.capture());
            assertEquals(new BigDecimal("-50.00"), entryCaptor.getValue().getAmount());
        }

        @Test
        void entryWithoutBranch_isSavedToBeCompletedLater() {
            ledgerService.record(entry(new BigDecimal("200"), null, "Combustible"));

            verify(ledgerEntryRepository).save(entryCaptor.capture());
            assertNull(entryCaptor.getValue().getBranchId());
        }

        @Test
        void longDescription_isTruncatedTo255() {
            ledgerService.record(entry(new BigDecimal("200"), BRANCH_ID, "x".repeat(300)));

            verify(ledgerEntryRepository).save(entryCaptor.capture());
            assertEquals("x".repeat(255), entryCaptor.getValue().getDescription());
        }

        @Test
        void entryWithoutDescription_isSaved() {
            ledgerService.record(entry(new BigDecimal("200"), BRANCH_ID, null));

            verify(ledgerEntryRepository).save(entryCaptor.capture());
            assertNull(entryCaptor.getValue().getDescription());
        }
    }

    @Nested
    class AssignBusBranch {

        @ParameterizedTest
        @ValueSource(ints = {0, 3})
        void delegatesToRepository(int updated) {
            when(ledgerEntryRepository.assignBranchToBusTrips(BUS_ID, BRANCH_ID)).thenReturn(updated);

            ledgerService.assignBusBranch(BUS_ID, BRANCH_ID);

            verify(ledgerEntryRepository).assignBranchToBusTrips(BUS_ID, BRANCH_ID);
        }
    }

    private static LedgerEntryEntity entry(BigDecimal amount, UUID branchId, String description) {
        return LedgerEntryEntity.builder()
                .type(LedgerEntryType.EXPENSE)
                .concept(LedgerConcept.FUEL)
                .branchId(branchId)
                .amount(amount)
                .occurredOn(TODAY)
                .sourceType(LedgerSourceType.TRIP_FUEL)
                .sourceId(SOURCE_ID)
                .description(description)
                .build();
    }
}
