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
import com.urbantransport.reporting.event.WorkshopExpenseRegisteredPayload;
import com.urbantransport.reporting.repository.DimBusRepository;
import com.urbantransport.reporting.repository.DimDepreciationRateRepository;
import com.urbantransport.reporting.repository.FactDepreciationRepository;
import com.urbantransport.reporting.repository.FactWorkshopLineRepository;
import com.urbantransport.reporting.repository.ProcessedEventRepository;
import com.urbantransport.reporting.service.FinanceFactService;
import com.urbantransport.reporting.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceFactServiceImpl implements FinanceFactService {

    private static final String PART = "PART";

    private final FactWorkshopLineRepository factWorkshopLineRepository;
    private final FactDepreciationRepository factDepreciationRepository;
    private final DimDepreciationRateRepository dimDepreciationRateRepository;
    private final DimBusRepository dimBusRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final LedgerService ledgerService;

    @Value("${app.zone:America/Guatemala}")
    private ZoneId zone;

    @Override
    @Transactional
    public void applyWorkshopExpense(UUID eventId, String eventType, WorkshopExpenseRegisteredPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }

        List<WorkshopExpenseRegisteredPayload.Line> lines = payload.lines() == null ? List.of() : payload.lines();
        for (WorkshopExpenseRegisteredPayload.Line line : lines) {
            factWorkshopLineRepository.save(FactWorkshopLineEntity.builder()
                    .lineId(line.lineId())
                    .expenseId(payload.expenseId())
                    .busId(payload.busId())
                    .branchId(payload.branchId())
                    .serviceDate(payload.serviceDate())
                    .categoryId(line.categoryId())
                    .categoryName(line.categoryName())
                    .type(line.type())
                    .description(line.description())
                    .amount(line.amount())
                    .build());

            ledgerService.record(LedgerEntryEntity.builder()
                    .type(LedgerEntryType.EXPENSE)
                    .concept(PART.equals(line.type()) ? LedgerConcept.SPARE_PARTS : LedgerConcept.WORKSHOP)
                    .branchId(payload.branchId())
                    .amount(line.amount())
                    .occurredOn(payload.serviceDate() != null ? payload.serviceDate() : LocalDate.now(zone))
                    .sourceType(LedgerSourceType.WORKSHOP_LINE)
                    .sourceId(line.lineId())
                    .description(line.description() == null ? line.categoryName()
                            : line.categoryName() + ": " + line.description())
                    .build());
        }

        markProcessed(eventId, eventType);
        log.info("Gasto de taller {} sincronizado: {} líneas", payload.expenseId(), lines.size());
    }

    @Override
    @Transactional
    public void applyDepreciationRateChanged(UUID eventId, String eventType, DepreciationRateChangedPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }

        dimDepreciationRateRepository.save(DimDepreciationRateEntity.builder()
                .rateId(payload.rateId())
                .amountPerKm(payload.amountPerKm())
                .effectiveFrom(payload.effectiveFrom())
                .build());

        markProcessed(eventId, eventType);
        log.info("Tasa de depreciación {} sincronizada: {} por km", payload.rateId(), payload.amountPerKm());
    }

    @Override
    @Transactional
    public void applyDepreciationCalculated(UUID eventId, String eventType, DepreciationCalculatedPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }

        factDepreciationRepository.save(FactDepreciationEntity.builder()
                .tripId(payload.tripId())
                .busId(payload.busId())
                .branchId(payload.branchId())
                .km(payload.km())
                .rateId(payload.rateId())
                .rateApplied(payload.rateApplied())
                .amount(payload.amount())
                .arrivedAt(payload.arrivedAt())
                .build());

        DimBusEntity bus = payload.busId() == null ? null : dimBusRepository.findById(payload.busId()).orElse(null);
        String plate = bus != null && bus.getPlate() != null ? " del bus " + bus.getPlate() : "";
        ledgerService.record(LedgerEntryEntity.builder()
                .type(LedgerEntryType.EXPENSE)
                .concept(LedgerConcept.DEPRECIATION)
                .branchId(bus != null ? bus.getBranchId() : null)
                .amount(payload.amount())
                .occurredOn(payload.arrivedAt() != null ? LocalDate.ofInstant(payload.arrivedAt(), zone)
                        : LocalDate.now(zone))
                .sourceType(LedgerSourceType.DEPRECIATION)
                .sourceId(payload.tripId())
                .description("Depreciación" + plate + ": " + plain(payload.km()) + " km a "
                        + plain(payload.rateApplied()) + " por km")
                .build());

        markProcessed(eventId, eventType);
        log.info("Depreciación del viaje {} sincronizada: {}", payload.tripId(), payload.amount());
    }

    private String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private void markProcessed(UUID eventId, String eventType) {
        processedEventRepository.save(ProcessedEventEntity.builder()
                .eventId(eventId)
                .eventType(eventType)
                .build());
    }
}
