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
import com.urbantransport.reporting.service.DimensionService;
import com.urbantransport.reporting.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DimensionServiceImpl implements DimensionService {

    private final DimBranchRepository dimBranchRepository;
    private final DimBusRepository dimBusRepository;
    private final DimDriverRepository dimDriverRepository;
    private final DimRouteRepository dimRouteRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final LedgerService ledgerService;

    @Override
    @Transactional
    public void applyBranch(UUID eventId, String eventType, BranchEventPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }

        DimBranchEntity branch = dimBranchRepository.findById(payload.branchId())
                .orElseGet(() -> DimBranchEntity.builder().branchId(payload.branchId()).build());
        branch.setName(payload.name());
        branch.setCode(payload.code());
        branch.setLatitude(payload.latitude());
        branch.setLongitude(payload.longitude());
        branch.setActive(payload.active());
        dimBranchRepository.save(branch);

        markProcessed(eventId, eventType);
        log.info("Sucursal {} sincronizada desde {}", payload.branchId(), eventType);
    }

    @Override
    @Transactional
    public void applyBus(UUID eventId, String eventType, BusEventPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }

        DimBusEntity bus = dimBusRepository.findById(payload.busId())
                .orElseGet(() -> DimBusEntity.builder().busId(payload.busId()).build());
        bus.setBranchId(payload.branchId());
        bus.setPlate(payload.plate());
        bus.setBrand(payload.brand());
        bus.setModel(payload.model());
        bus.setManufactureYear(payload.manufactureYear());
        bus.setCapacity(payload.capacity());
        bus.setStatus(payload.status());
        bus.setOdometerKm(payload.odometerKm());
        dimBusRepository.save(bus);

        if (payload.branchId() != null) {
            ledgerService.assignBusBranch(payload.busId(), payload.branchId());
        }

        markProcessed(eventId, eventType);
        log.info("Bus {} sincronizado desde {}", payload.busId(), eventType);
    }

    @Override
    @Transactional
    public void applyDriver(UUID eventId, String eventType, DriverEventPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }

        DimDriverEntity driver = dimDriverRepository.findById(payload.driverId())
                .orElseGet(() -> DimDriverEntity.builder().driverId(payload.driverId()).build());
        driver.setUserId(payload.userId());
        driver.setBranchId(payload.branchId());
        driver.setFullName(payload.fullName());
        driver.setLicenseNumber(payload.licenseNumber());
        driver.setLicenseType(payload.licenseType());
        driver.setLicenseExpiresOn(payload.licenseExpiresOn());
        driver.setActive(payload.active());
        dimDriverRepository.save(driver);

        markProcessed(eventId, eventType);
        log.info("Chofer {} sincronizado desde {}", payload.driverId(), eventType);
    }

    @Override
    @Transactional
    public void applyRoute(UUID eventId, String eventType, RouteEventPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }

        DimRouteEntity route = dimRouteRepository.findById(payload.routeId())
                .orElseGet(() -> DimRouteEntity.builder().routeId(payload.routeId()).build());
        route.setOriginBranchId(payload.originBranchId());
        route.setDestinationBranchId(payload.destinationBranchId());
        route.setDistanceKm(payload.distanceKm());
        route.setTicketPrice(payload.ticketPrice());
        route.setDeleted(EventTypes.ROUTE_DELETED.equals(eventType) || Boolean.TRUE.equals(payload.deleted()));
        dimRouteRepository.save(route);

        markProcessed(eventId, eventType);
        log.info("Ruta {} sincronizada desde {}", payload.routeId(), eventType);
    }

    private void markProcessed(UUID eventId, String eventType) {
        processedEventRepository.save(ProcessedEventEntity.builder()
                .eventId(eventId)
                .eventType(eventType)
                .build());
    }
}
