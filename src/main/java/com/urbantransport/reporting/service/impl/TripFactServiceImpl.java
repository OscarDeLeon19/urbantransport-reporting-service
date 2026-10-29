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
import com.urbantransport.reporting.service.TripFactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripFactServiceImpl implements TripFactService {

    private static final String PRIVATE_TRIP = "PRIVATE";

    private final FactTripRepository factTripRepository;
    private final FactTicketSaleRepository factTicketSaleRepository;
    private final DimBusRepository dimBusRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final LedgerService ledgerService;

    @Value("${app.zone:America/Guatemala}")
    private ZoneId zone;

    @Override
    @Transactional
    public void applyTripEvent(UUID eventId, String eventType, TripEventPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }

        FactTripEntity trip = factTripRepository.findById(payload.tripId())
                .orElseGet(() -> FactTripEntity.builder().tripId(payload.tripId()).build());
        merge(trip, payload);
        factTripRepository.save(trip);

        factTicketSaleRepository.syncTrip(trip.getTripId(), trip.getRouteId(), trip.getBusId());

        if (EventTypes.TRIP_COMPLETED.equals(eventType)) {
            recordTripExpenses(trip);
        }

        markProcessed(eventId, eventType);
        log.info("Viaje {} sincronizado desde {}", payload.tripId(), eventType);
    }

    private void merge(FactTripEntity trip, TripEventPayload payload) {
        if (payload.type() != null) {
            trip.setType(payload.type());
        }
        if (payload.routeId() != null) {
            trip.setRouteId(payload.routeId());
        }
        if (payload.rentalId() != null) {
            trip.setRentalId(payload.rentalId());
        }
        if (payload.originBranchId() != null) {
            trip.setOriginBranchId(payload.originBranchId());
        }
        if (payload.destinationBranchId() != null) {
            trip.setDestinationBranchId(payload.destinationBranchId());
        }
        if (payload.busId() != null) {
            trip.setBusId(payload.busId());
        }
        if (payload.driverId() != null) {
            trip.setDriverId(payload.driverId());
        }
        if (payload.ticketPrice() != null) {
            trip.setTicketPrice(payload.ticketPrice());
        }
        if (payload.departureAt() != null) {
            trip.setScheduledDeparture(payload.departureAt().atZone(zone).toInstant());
        }
        if (payload.actualDepartureAt() != null) {
            trip.setActualDepartureAt(payload.actualDepartureAt());
        }
        if (payload.actualArrivalAt() != null) {
            trip.setActualArrivalAt(payload.actualArrivalAt());
        }
        if (payload.kmTraveled() != null) {
            trip.setKmTraveled(payload.kmTraveled());
        }
        if (payload.fuelCost() != null) {
            trip.setFuelCost(payload.fuelCost());
        }
        if (payload.driverPay() != null) {
            trip.setDriverPay(payload.driverPay());
        }
        if (payload.status() != null) {
            trip.setStatus(payload.status());
        }
    }

    private void recordTripExpenses(FactTripEntity trip) {
        DimBusEntity bus = trip.getBusId() == null ? null : dimBusRepository.findById(trip.getBusId()).orElse(null);
        UUID branchId = bus != null ? bus.getBranchId() : null;
        LocalDate occurredOn = trip.getActualArrivalAt() != null
                ? LocalDate.ofInstant(trip.getActualArrivalAt(), zone)
                : LocalDate.now(zone);
        String tripLabel = tripLabel(trip, bus);

        ledgerService.record(LedgerEntryEntity.builder()
                .type(LedgerEntryType.EXPENSE)
                .concept(LedgerConcept.FUEL)
                .branchId(branchId)
                .amount(trip.getFuelCost())
                .occurredOn(occurredOn)
                .sourceType(LedgerSourceType.TRIP_FUEL)
                .sourceId(trip.getTripId())
                .description("Combustible del " + tripLabel)
                .build());

        ledgerService.record(LedgerEntryEntity.builder()
                .type(LedgerEntryType.EXPENSE)
                .concept(LedgerConcept.DRIVER_PAY)
                .branchId(branchId)
                .amount(trip.getDriverPay())
                .occurredOn(occurredOn)
                .sourceType(LedgerSourceType.TRIP_DRIVER_PAY)
                .sourceId(trip.getTripId())
                .description("Pago del chofer del " + tripLabel)
                .build());
    }

    private String tripLabel(FactTripEntity trip, DimBusEntity bus) {
        String label = PRIVATE_TRIP.equals(trip.getType()) ? "viaje privado" : "viaje regular";
        if (bus != null && bus.getPlate() != null) {
            label += " del bus " + bus.getPlate();
        }
        if (trip.getKmTraveled() != null) {
            label += " (" + trip.getKmTraveled().stripTrailingZeros().toPlainString() + " km)";
        }
        return label;
    }

    private void markProcessed(UUID eventId, String eventType) {
        processedEventRepository.save(ProcessedEventEntity.builder()
                .eventId(eventId)
                .eventType(eventType)
                .build());
    }
}
