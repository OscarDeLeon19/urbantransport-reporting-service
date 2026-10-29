package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.FactRentalEntity;
import com.urbantransport.reporting.domain.LedgerConcept;
import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerEntryType;
import com.urbantransport.reporting.domain.LedgerSourceType;
import com.urbantransport.reporting.domain.ProcessedEventEntity;
import com.urbantransport.reporting.event.RentalAssignedPayload;
import com.urbantransport.reporting.event.RentalEventPayload;
import com.urbantransport.reporting.repository.FactRentalRepository;
import com.urbantransport.reporting.repository.ProcessedEventRepository;
import com.urbantransport.reporting.service.LedgerService;
import com.urbantransport.reporting.service.RentalFactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentalFactServiceImpl implements RentalFactService {

    private static final String ASSIGNED = "ASSIGNED";

    private final FactRentalRepository factRentalRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final LedgerService ledgerService;

    @Value("${app.zone:America/Guatemala}")
    private ZoneId zone;

    @Override
    @Transactional
    public void applyRentalPaid(UUID eventId, String eventType, Instant occurredAt, RentalEventPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }

        FactRentalEntity rental = factRentalRepository.findById(payload.rentalId())
                .orElseGet(() -> FactRentalEntity.builder().rentalId(payload.rentalId()).build());
        rental.setBranchId(payload.branchId());
        rental.setChannel(payload.channel());
        rental.setCustomerNit(payload.customerNit());
        rental.setOriginText(payload.originText());
        rental.setDestinationText(payload.destinationText());
        rental.setAmount(payload.confirmedPrice());
        rental.setPaymentDate(payload.paymentDate());
        rental.setDepartureDate(payload.departureDate());
        rental.setReturnDate(payload.returnDate());
        rental.setPassengers(payload.passengers());
        rental.setDistanceKm(payload.distanceKm());
        if (rental.getBusId() == null) {
            rental.setStatus(payload.status());
        }
        factRentalRepository.save(rental);

        LocalDate occurredOn = payload.paymentDate() != null ? payload.paymentDate()
                : occurredAt != null ? LocalDate.ofInstant(occurredAt, zone) : LocalDate.now(zone);
        ledgerService.record(LedgerEntryEntity.builder()
                .type(LedgerEntryType.INCOME)
                .concept(LedgerConcept.RENTAL)
                .branchId(payload.branchId())
                .amount(payload.confirmedPrice())
                .occurredOn(occurredOn)
                .sourceType(LedgerSourceType.RENTAL)
                .sourceId(payload.rentalId())
                .description("Alquiler NIT " + payload.customerNit() + ": " + payload.originText() + " - "
                        + payload.destinationText())
                .build());

        markProcessed(eventId, eventType);
        log.info("Alquiler {} sincronizado desde {}", payload.rentalId(), eventType);
    }

    @Override
    @Transactional
    public void applyRentalAssigned(UUID eventId, String eventType, RentalAssignedPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }

        FactRentalEntity rental = factRentalRepository.findById(payload.rentalId())
                .orElseGet(() -> FactRentalEntity.builder()
                        .rentalId(payload.rentalId())
                        .branchId(payload.branchId())
                        .build());
        rental.setBusId(payload.busId());
        rental.setStatus(ASSIGNED);
        factRentalRepository.save(rental);

        markProcessed(eventId, eventType);
        log.info("Alquiler {} asignado al bus {}", payload.rentalId(), payload.busId());
    }

    private void markProcessed(UUID eventId, String eventType) {
        processedEventRepository.save(ProcessedEventEntity.builder()
                .eventId(eventId)
                .eventType(eventType)
                .build());
    }
}
