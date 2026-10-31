package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.DimBusEntity;
import com.urbantransport.reporting.domain.DimDepreciationRateEntity;
import com.urbantransport.reporting.domain.FactDepreciationEntity;
import com.urbantransport.reporting.domain.FactWorkshopLineEntity;
import com.urbantransport.reporting.domain.LedgerConcept;
import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerEntryType;
import com.urbantransport.reporting.domain.LedgerSourceType;
import com.urbantransport.reporting.domain.ProcessedEventEntity;
import com.urbantransport.reporting.event.DepreciationCalculatedPayload;
import com.urbantransport.reporting.event.DepreciationRateChangedPayload;
import com.urbantransport.reporting.event.EventTypes;
import com.urbantransport.reporting.event.WorkshopExpenseRegisteredPayload;
import com.urbantransport.reporting.repository.DimBusRepository;
import com.urbantransport.reporting.repository.DimDepreciationRateRepository;
import com.urbantransport.reporting.repository.FactDepreciationRepository;
import com.urbantransport.reporting.repository.FactWorkshopLineRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceFactServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EXPENSE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID BRANCH_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID LABOR_LINE_ID = UUID.fromString("55555555-5555-5555-5555-555555555551");
    private static final UUID PART_LINE_ID = UUID.fromString("55555555-5555-5555-5555-555555555552");
    private static final UUID CATEGORY_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID RATE_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID TRIP_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID TRIP_BRANCH_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final LocalDate TODAY = LocalDate.now(ZONE);
    private static final LocalDate SERVICE_DATE = TODAY.minusDays(4);
    private static final Instant ARRIVED_AT = TODAY.minusDays(2).atTime(21, 15).atZone(ZONE).toInstant();

    @Mock
    private FactWorkshopLineRepository factWorkshopLineRepository;

    @Mock
    private FactDepreciationRepository factDepreciationRepository;

    @Mock
    private DimDepreciationRateRepository dimDepreciationRateRepository;

    @Mock
    private DimBusRepository dimBusRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private LedgerService ledgerService;

    @Captor
    private ArgumentCaptor<LedgerEntryEntity> entryCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEventEntity> processedEventCaptor;

    private FinanceFactServiceImpl financeFactService;

    @BeforeEach
    void setUp() {
        financeFactService = new FinanceFactServiceImpl(factWorkshopLineRepository, factDepreciationRepository,
                dimDepreciationRateRepository, dimBusRepository, processedEventRepository, ledgerService);
        ReflectionTestUtils.setField(financeFactService, "zone", ZONE);
    }

    @Nested
    class ApplyWorkshopExpense {

        @Test
        void eachLine_isSavedAndRecordedAsWorkshopOrSpareParts() {
            financeFactService.applyWorkshopExpense(EVENT_ID, EventTypes.WORKSHOP_EXPENSE_REGISTERED,
                    expense(SERVICE_DATE, List.of(
                            line(LABOR_LINE_ID, "LABOR", "Ajuste de frenos", "150.00"),
                            line(PART_LINE_ID, "PART", null, "350.00"))));

            ArgumentCaptor<FactWorkshopLineEntity> lineCaptor = ArgumentCaptor.forClass(FactWorkshopLineEntity.class);
            verify(factWorkshopLineRepository, times(2)).save(lineCaptor.capture());
            FactWorkshopLineEntity labor = lineCaptor.getAllValues().get(0);
            assertEquals(LABOR_LINE_ID, labor.getLineId());
            assertEquals(EXPENSE_ID, labor.getExpenseId());
            assertEquals(BUS_ID, labor.getBusId());
            assertEquals(BRANCH_ID, labor.getBranchId());
            assertEquals(SERVICE_DATE, labor.getServiceDate());
            assertEquals(CATEGORY_ID, labor.getCategoryId());
            assertEquals("Frenos", labor.getCategoryName());
            assertEquals("LABOR", labor.getType());
            assertEquals("Ajuste de frenos", labor.getDescription());
            assertEquals(new BigDecimal("150.00"), labor.getAmount());

            verify(ledgerService, times(2)).record(entryCaptor.capture());
            LedgerEntryEntity workshop = entryCaptor.getAllValues().get(0);
            assertEquals(LedgerEntryType.EXPENSE, workshop.getType());
            assertEquals(LedgerConcept.WORKSHOP, workshop.getConcept());
            assertEquals(LedgerSourceType.WORKSHOP_LINE, workshop.getSourceType());
            assertEquals(LABOR_LINE_ID, workshop.getSourceId());
            assertEquals(BRANCH_ID, workshop.getBranchId());
            assertEquals(SERVICE_DATE, workshop.getOccurredOn());
            assertEquals("Frenos: Ajuste de frenos", workshop.getDescription());
            LedgerEntryEntity parts = entryCaptor.getAllValues().get(1);
            assertEquals(LedgerConcept.SPARE_PARTS, parts.getConcept());
            assertEquals(PART_LINE_ID, parts.getSourceId());
            assertEquals(new BigDecimal("350.00"), parts.getAmount());
            assertEquals("Frenos", parts.getDescription());
            assertMarkedProcessed(EventTypes.WORKSHOP_EXPENSE_REGISTERED);
        }

        @Test
        void withoutServiceDate_recordsToday() {
            financeFactService.applyWorkshopExpense(EVENT_ID, EventTypes.WORKSHOP_EXPENSE_REGISTERED,
                    expense(null, List.of(line(LABOR_LINE_ID, "LABOR", "Ajuste", "150.00"))));

            verify(ledgerService).record(entryCaptor.capture());
            assertEquals(TODAY, entryCaptor.getValue().getOccurredOn());
        }

        @Test
        void expenseWithoutLines_onlyMarksProcessed() {
            financeFactService.applyWorkshopExpense(EVENT_ID, EventTypes.WORKSHOP_EXPENSE_REGISTERED,
                    expense(SERVICE_DATE, null));

            verifyNoInteractions(factWorkshopLineRepository, ledgerService);
            assertMarkedProcessed(EventTypes.WORKSHOP_EXPENSE_REGISTERED);
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            financeFactService.applyWorkshopExpense(EVENT_ID, EventTypes.WORKSHOP_EXPENSE_REGISTERED,
                    expense(SERVICE_DATE, List.of(line(LABOR_LINE_ID, "LABOR", "Ajuste", "150.00"))));

            verifyNoInteractions(factWorkshopLineRepository, ledgerService);
            verify(processedEventRepository, never()).save(any());
        }
    }

    @Nested
    class ApplyDepreciationRateChanged {

        @Test
        void rate_isSaved() {
            Instant effectiveFrom = TODAY.atStartOfDay(ZONE).toInstant();

            financeFactService.applyDepreciationRateChanged(EVENT_ID, EventTypes.DEPRECIATION_RATE_CHANGED,
                    new DepreciationRateChangedPayload(RATE_ID, new BigDecimal("0.7500"), effectiveFrom));

            ArgumentCaptor<DimDepreciationRateEntity> captor = ArgumentCaptor.forClass(DimDepreciationRateEntity.class);
            verify(dimDepreciationRateRepository).save(captor.capture());
            assertEquals(RATE_ID, captor.getValue().getRateId());
            assertEquals(new BigDecimal("0.7500"), captor.getValue().getAmountPerKm());
            assertEquals(effectiveFrom, captor.getValue().getEffectiveFrom());
            assertMarkedProcessed(EventTypes.DEPRECIATION_RATE_CHANGED);
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            financeFactService.applyDepreciationRateChanged(EVENT_ID, EventTypes.DEPRECIATION_RATE_CHANGED,
                    new DepreciationRateChangedPayload(RATE_ID, new BigDecimal("0.7500"), Instant.now()));

            verifyNoInteractions(dimDepreciationRateRepository);
            verify(processedEventRepository, never()).save(any());
        }
    }

    @Nested
    class ApplyDepreciationCalculated {

        @Test
        void knownBus_savesFactWithTripBranchAndRecordsInBusBranch() {
            when(dimBusRepository.findById(BUS_ID)).thenReturn(Optional.of(bus("P-123ABC")));

            financeFactService.applyDepreciationCalculated(EVENT_ID, EventTypes.DEPRECIATION_CALCULATED,
                    depreciation(BUS_ID, new BigDecimal("120.50"), new BigDecimal("0.7500"), ARRIVED_AT));

            ArgumentCaptor<FactDepreciationEntity> captor = ArgumentCaptor.forClass(FactDepreciationEntity.class);
            verify(factDepreciationRepository).save(captor.capture());
            FactDepreciationEntity fact = captor.getValue();
            assertEquals(TRIP_ID, fact.getTripId());
            assertEquals(BUS_ID, fact.getBusId());
            assertEquals(TRIP_BRANCH_ID, fact.getBranchId());
            assertEquals(new BigDecimal("120.50"), fact.getKm());
            assertEquals(RATE_ID, fact.getRateId());
            assertEquals(new BigDecimal("0.7500"), fact.getRateApplied());
            assertEquals(new BigDecimal("90.38"), fact.getAmount());
            assertEquals(ARRIVED_AT, fact.getArrivedAt());

            verify(ledgerService).record(entryCaptor.capture());
            LedgerEntryEntity entry = entryCaptor.getValue();
            assertEquals(LedgerEntryType.EXPENSE, entry.getType());
            assertEquals(LedgerConcept.DEPRECIATION, entry.getConcept());
            assertEquals(LedgerSourceType.DEPRECIATION, entry.getSourceType());
            assertEquals(TRIP_ID, entry.getSourceId());
            assertEquals(BRANCH_ID, entry.getBranchId());
            assertEquals(new BigDecimal("90.38"), entry.getAmount());
            assertEquals(TODAY.minusDays(2), entry.getOccurredOn());
            assertEquals("Depreciación del bus P-123ABC: 120.5 km a 0.75 por km", entry.getDescription());
            assertMarkedProcessed(EventTypes.DEPRECIATION_CALCULATED);
        }

        @Test
        void unknownBus_recordsWithoutBranchToCompleteLater() {
            when(dimBusRepository.findById(BUS_ID)).thenReturn(Optional.empty());

            financeFactService.applyDepreciationCalculated(EVENT_ID, EventTypes.DEPRECIATION_CALCULATED,
                    depreciation(BUS_ID, new BigDecimal("120.50"), new BigDecimal("0.7500"), ARRIVED_AT));

            verify(ledgerService).record(entryCaptor.capture());
            assertNull(entryCaptor.getValue().getBranchId());
            assertEquals("Depreciación: 120.5 km a 0.75 por km", entryCaptor.getValue().getDescription());
        }

        @Test
        void busWithoutPlate_omitsPlate() {
            when(dimBusRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(null)));

            financeFactService.applyDepreciationCalculated(EVENT_ID, EventTypes.DEPRECIATION_CALCULATED,
                    depreciation(BUS_ID, new BigDecimal("120.50"), new BigDecimal("0.7500"), ARRIVED_AT));

            verify(ledgerService).record(entryCaptor.capture());
            assertEquals(BRANCH_ID, entryCaptor.getValue().getBranchId());
            assertEquals("Depreciación: 120.5 km a 0.75 por km", entryCaptor.getValue().getDescription());
        }

        @Test
        void withoutBusKmRateOrArrival_usesPlaceholdersAndToday() {
            financeFactService.applyDepreciationCalculated(EVENT_ID, EventTypes.DEPRECIATION_CALCULATED,
                    depreciation(null, null, null, null));

            verifyNoInteractions(dimBusRepository);
            verify(ledgerService).record(entryCaptor.capture());
            assertNull(entryCaptor.getValue().getBranchId());
            assertEquals(TODAY, entryCaptor.getValue().getOccurredOn());
            assertEquals("Depreciación: - km a - por km", entryCaptor.getValue().getDescription());
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            financeFactService.applyDepreciationCalculated(EVENT_ID, EventTypes.DEPRECIATION_CALCULATED,
                    depreciation(BUS_ID, new BigDecimal("120.50"), new BigDecimal("0.7500"), ARRIVED_AT));

            verifyNoInteractions(factDepreciationRepository, dimBusRepository, ledgerService);
            verify(processedEventRepository, never()).save(any());
        }
    }

    private static WorkshopExpenseRegisteredPayload expense(LocalDate serviceDate,
                                                            List<WorkshopExpenseRegisteredPayload.Line> lines) {
        return new WorkshopExpenseRegisteredPayload(EXPENSE_ID, BUS_ID, BRANCH_ID, serviceDate,
                new BigDecimal("500.00"), lines);
    }

    private static WorkshopExpenseRegisteredPayload.Line line(UUID lineId, String type, String description,
                                                              String amount) {
        return new WorkshopExpenseRegisteredPayload.Line(lineId, CATEGORY_ID, "Frenos", description, type,
                new BigDecimal(amount));
    }

    private static DepreciationCalculatedPayload depreciation(UUID busId, BigDecimal km, BigDecimal rate,
                                                              Instant arrivedAt) {
        return new DepreciationCalculatedPayload(TRIP_ID, busId, TRIP_BRANCH_ID, km, RATE_ID, rate,
                new BigDecimal("90.38"), arrivedAt);
    }

    private static DimBusEntity bus(String plate) {
        return DimBusEntity.builder().busId(BUS_ID).branchId(BRANCH_ID).plate(plate).build();
    }

    private void assertMarkedProcessed(String eventType) {
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
        assertEquals(eventType, processedEventCaptor.getValue().getEventType());
    }
}
