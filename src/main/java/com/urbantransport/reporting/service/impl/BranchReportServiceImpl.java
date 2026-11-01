package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.BusStatus;
import com.urbantransport.reporting.domain.DimBranchEntity;
import com.urbantransport.reporting.domain.DimBusEntity;
import com.urbantransport.reporting.domain.DimDepreciationRateEntity;
import com.urbantransport.reporting.domain.DimDriverEntity;
import com.urbantransport.reporting.domain.DimRouteEntity;
import com.urbantransport.reporting.domain.FactDepreciationEntity;
import com.urbantransport.reporting.domain.FactRentalEntity;
import com.urbantransport.reporting.domain.FactTicketSaleEntity;
import com.urbantransport.reporting.domain.FactTripEntity;
import com.urbantransport.reporting.domain.FactWorkshopLineEntity;
import com.urbantransport.reporting.domain.TripStatus;
import com.urbantransport.reporting.dto.BusDepreciationReportResponse;
import com.urbantransport.reporting.dto.BusListReportResponse;
import com.urbantransport.reporting.dto.DriverListReportResponse;
import com.urbantransport.reporting.dto.RentalReportResponse;
import com.urbantransport.reporting.dto.TicketSalesReportResponse;
import com.urbantransport.reporting.dto.WorkshopBusReportResponse;
import com.urbantransport.reporting.exception.ApiException;
import com.urbantransport.reporting.exception.ErrorCodes;
import com.urbantransport.reporting.repository.DimBranchRepository;
import com.urbantransport.reporting.repository.DimBusRepository;
import com.urbantransport.reporting.repository.DimDepreciationRateRepository;
import com.urbantransport.reporting.repository.DimDriverRepository;
import com.urbantransport.reporting.repository.DimRouteRepository;
import com.urbantransport.reporting.repository.FactDepreciationRepository;
import com.urbantransport.reporting.repository.FactDepreciationSpecifications;
import com.urbantransport.reporting.repository.FactRentalRepository;
import com.urbantransport.reporting.repository.FactRentalSpecifications;
import com.urbantransport.reporting.repository.FactTicketSaleRepository;
import com.urbantransport.reporting.repository.FactTicketSaleSpecifications;
import com.urbantransport.reporting.repository.FactTripRepository;
import com.urbantransport.reporting.repository.FactWorkshopLineRepository;
import com.urbantransport.reporting.repository.FactWorkshopLineSpecifications;
import com.urbantransport.reporting.repository.IdCountView;
import com.urbantransport.reporting.service.BranchReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class BranchReportServiceImpl implements BranchReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final DimBusRepository dimBusRepository;
    private final DimDriverRepository dimDriverRepository;
    private final DimRouteRepository dimRouteRepository;
    private final DimBranchRepository dimBranchRepository;
    private final DimDepreciationRateRepository dimDepreciationRateRepository;
    private final FactTripRepository factTripRepository;
    private final FactTicketSaleRepository factTicketSaleRepository;
    private final FactRentalRepository factRentalRepository;
    private final FactDepreciationRepository factDepreciationRepository;
    private final FactWorkshopLineRepository factWorkshopLineRepository;

    @Value("${app.zone:America/Guatemala}")
    private ZoneId zone;

    @Override
    @Transactional(readOnly = true)
    public BusListReportResponse buses(UUID branchId, BusStatus status) {
        List<DimBusEntity> buses = status == null
                ? dimBusRepository.findByBranchIdOrderByPlateAsc(branchId)
                : dimBusRepository.findByBranchIdAndStatusOrderByPlateAsc(branchId, status.name());
        if (buses.isEmpty()) {
            return new BusListReportResponse(List.of(), 0);
        }

        List<UUID> busIds = buses.stream().map(DimBusEntity::getBusId).toList();
        Map<UUID, Long> completedTrips = counts(factTripRepository.countByBus(busIds, TripStatus.COMPLETED.name()));
        Map<UUID, UUID> currentDrivers = currentDrivers(busIds);
        Map<UUID, String> driverNames = dimDriverRepository.findAllById(currentDrivers.values()).stream()
                .filter(driver -> driver.getFullName() != null)
                .collect(Collectors.toMap(DimDriverEntity::getDriverId, DimDriverEntity::getFullName));

        List<BusListReportResponse.BusRow> rows = buses.stream()
                .map(bus -> {
                    UUID driverId = currentDrivers.get(bus.getBusId());
                    return new BusListReportResponse.BusRow(
                            bus.getBusId(),
                            bus.getPlate(),
                            bus.getBrand(),
                            bus.getModel(),
                            bus.getCapacity(),
                            bus.getStatus(),
                            driverId,
                            driverId == null ? null : driverNames.get(driverId),
                            bus.getOdometerKm(),
                            completedTrips.getOrDefault(bus.getBusId(), 0L));
                })
                .toList();

        return new BusListReportResponse(rows,
                rows.stream().mapToLong(BusListReportResponse.BusRow::getCompletedTrips).sum());
    }

    @Override
    @Transactional(readOnly = true)
    public DriverListReportResponse drivers(UUID branchId) {
        List<DimDriverEntity> drivers = dimDriverRepository.findByBranchIdOrderByFullNameAsc(branchId);
        if (drivers.isEmpty()) {
            return new DriverListReportResponse(List.of(), 0);
        }

        Map<UUID, Long> completedTrips = counts(factTripRepository.countByDriver(
                drivers.stream().map(DimDriverEntity::getDriverId).toList(), TripStatus.COMPLETED.name()));

        List<DriverListReportResponse.DriverRow> rows = drivers.stream()
                .map(driver -> new DriverListReportResponse.DriverRow(
                        driver.getDriverId(),
                        driver.getLicenseNumber(),
                        driver.getFullName(),
                        driver.getLicenseType(),
                        driver.getLicenseExpiresOn(),
                        driver.getActive(),
                        completedTrips.getOrDefault(driver.getDriverId(), 0L)))
                .toList();

        return new DriverListReportResponse(rows,
                rows.stream().mapToLong(DriverListReportResponse.DriverRow::getCompletedTrips).sum());
    }

    @Override
    @Transactional(readOnly = true)
    public TicketSalesReportResponse ticketSales(UUID branchId, LocalDate from, LocalDate to, UUID routeId,
                                                 UUID busId) {
        validateRange(from, to);
        Map<UUID, List<FactTicketSaleEntity>> salesByTrip = factTicketSaleRepository
                .findAll(FactTicketSaleSpecifications.activeSales(branchId, routeId, busId, from, to))
                .stream()
                .filter(sale -> sale.getTripId() != null)
                .collect(Collectors.groupingBy(FactTicketSaleEntity::getTripId));
        Map<UUID, FactTripEntity> trips = factTripRepository.findAllById(salesByTrip.keySet()).stream()
                .collect(Collectors.toMap(FactTripEntity::getTripId, Function.identity()));
        Map<UUID, DimRouteEntity> routes = dimRouteRepository.findAllById(salesByTrip.values().stream()
                        .map(sales -> sales.getFirst().getRouteId())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(DimRouteEntity::getRouteId, Function.identity()));
        Map<UUID, String> branchNames = branchNames();
        Map<UUID, String> plates = plates(salesByTrip.values().stream()
                .map(sales -> sales.getFirst().getBusId())
                .toList());

        List<TicketSalesReportResponse.TripSales> rows = salesByTrip.entrySet().stream()
                .map(entry -> {
                    FactTripEntity trip = trips.get(entry.getKey());
                    FactTicketSaleEntity first = entry.getValue().getFirst();
                    DimRouteEntity route = first.getRouteId() == null ? null : routes.get(first.getRouteId());
                    UUID originId = trip != null && trip.getOriginBranchId() != null ? trip.getOriginBranchId()
                            : route != null ? route.getOriginBranchId() : null;
                    UUID destinationId = trip != null && trip.getDestinationBranchId() != null
                            ? trip.getDestinationBranchId()
                            : route != null ? route.getDestinationBranchId() : null;
                    return new TicketSalesReportResponse.TripSales(
                            entry.getKey(),
                            first.getRouteId(),
                            originId == null ? null : branchNames.get(originId),
                            destinationId == null ? null : branchNames.get(destinationId),
                            trip != null ? trip.getScheduledDeparture() : null,
                            first.getBusId(),
                            first.getBusId() == null ? null : plates.get(first.getBusId()),
                            entry.getValue().size(),
                            sum(entry.getValue().stream().map(FactTicketSaleEntity::getPricePaid)));
                })
                .sorted(Comparator.comparing(TicketSalesReportResponse.TripSales::getDepartureAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new TicketSalesReportResponse(rows,
                rows.stream().mapToLong(TicketSalesReportResponse.TripSales::getTicketsSold).sum(),
                sum(rows.stream().map(TicketSalesReportResponse.TripSales::getIncome)));
    }

    @Override
    @Transactional(readOnly = true)
    public RentalReportResponse rentals(UUID branchId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        List<FactRentalEntity> rentals = factRentalRepository.findAll(
                        FactRentalSpecifications.filter(branchId, from, to), Sort.by("paymentDate", "departureDate"))
                .stream()
                .filter(rental -> rental.getAmount() != null)
                .toList();
        Map<UUID, String> plates = plates(rentals.stream().map(FactRentalEntity::getBusId).toList());

        List<RentalReportResponse.RentalRow> rows = rentals.stream()
                .map(rental -> new RentalReportResponse.RentalRow(
                        rental.getRentalId(),
                        rental.getChannel(),
                        rental.getCustomerNit(),
                        rental.getOriginText(),
                        rental.getDestinationText(),
                        rental.getDepartureDate(),
                        rental.getReturnDate(),
                        rental.getPaymentDate(),
                        rental.getBusId(),
                        rental.getBusId() == null ? null : plates.get(rental.getBusId()),
                        rental.getAmount()))
                .toList();

        return new RentalReportResponse(rows, sum(rows.stream().map(RentalReportResponse.RentalRow::getAmount)));
    }

    @Override
    @Transactional(readOnly = true)
    public BusDepreciationReportResponse busDepreciation(UUID branchId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        DimDepreciationRateEntity rate = dimDepreciationRateRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(Instant.now())
                .orElse(null);
        BigDecimal amountPerKm = rate != null ? rate.getAmountPerKm() : null;
        Instant rateEffectiveFrom = rate != null ? rate.getEffectiveFrom() : null;

        List<DimBusEntity> buses = dimBusRepository.findByBranchIdOrderByPlateAsc(branchId);
        if (buses.isEmpty()) {
            return new BusDepreciationReportResponse(amountPerKm, rateEffectiveFrom, List.of(), ZERO, ZERO);
        }

        Instant fromInstant = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant toExclusive = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        Map<UUID, List<FactDepreciationEntity>> byBus = factDepreciationRepository
                .findAll(FactDepreciationSpecifications.filter(
                        buses.stream().map(DimBusEntity::getBusId).toList(), fromInstant, toExclusive))
                .stream()
                .collect(Collectors.groupingBy(FactDepreciationEntity::getBusId));

        List<BusDepreciationReportResponse.BusDepreciation> rows = buses.stream()
                .map(bus -> {
                    List<FactDepreciationEntity> depreciations = byBus.getOrDefault(bus.getBusId(), List.of());
                    return new BusDepreciationReportResponse.BusDepreciation(
                            bus.getBusId(),
                            bus.getPlate(),
                            sum(depreciations.stream().map(FactDepreciationEntity::getKm)),
                            amountPerKm,
                            sum(depreciations.stream().map(FactDepreciationEntity::getAmount)));
                })
                .toList();

        return new BusDepreciationReportResponse(amountPerKm, rateEffectiveFrom, rows,
                sum(rows.stream().map(BusDepreciationReportResponse.BusDepreciation::getKm)),
                sum(rows.stream().map(BusDepreciationReportResponse.BusDepreciation::getAccumulated)));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkshopBusReportResponse workshopByBus(UUID branchId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        List<FactWorkshopLineEntity> lines = factWorkshopLineRepository.findAll(
                FactWorkshopLineSpecifications.filter(branchId, null, from, to));
        Map<UUID, String> plates = plates(lines.stream().map(FactWorkshopLineEntity::getBusId).toList());

        Map<UUID, List<FactWorkshopLineEntity>> byBus = new HashMap<>();
        for (FactWorkshopLineEntity line : lines) {
            byBus.computeIfAbsent(line.getBusId(), id -> new ArrayList<>()).add(line);
        }

        List<WorkshopBusReportResponse.BusGroup> buses = byBus.entrySet().stream()
                .map(entry -> {
                    List<WorkshopBusReportResponse.CategoryTotal> categories = categoryTotals(entry.getValue());
                    return new WorkshopBusReportResponse.BusGroup(
                            entry.getKey(),
                            entry.getKey() == null ? null : plates.get(entry.getKey()),
                            categories,
                            sum(categories.stream().map(WorkshopBusReportResponse.CategoryTotal::getAmount)));
                })
                .sorted(Comparator.comparing(WorkshopBusReportResponse.BusGroup::getTotal).reversed())
                .toList();

        return new WorkshopBusReportResponse(buses,
                sum(buses.stream().map(WorkshopBusReportResponse.BusGroup::getTotal)));
    }

    private List<WorkshopBusReportResponse.CategoryTotal> categoryTotals(List<FactWorkshopLineEntity> lines) {
        Map<UUID, List<FactWorkshopLineEntity>> byCategory = new HashMap<>();
        for (FactWorkshopLineEntity line : lines) {
            byCategory.computeIfAbsent(line.getCategoryId(), id -> new ArrayList<>()).add(line);
        }
        return byCategory.entrySet().stream()
                .map(entry -> new WorkshopBusReportResponse.CategoryTotal(
                        entry.getKey(),
                        entry.getValue().getFirst().getCategoryName(),
                        entry.getValue().size(),
                        sum(entry.getValue().stream().map(FactWorkshopLineEntity::getAmount))))
                .sorted(Comparator.comparing(WorkshopBusReportResponse.CategoryTotal::getAmount).reversed())
                .toList();
    }

    private Map<UUID, UUID> currentDrivers(List<UUID> busIds) {
        List<FactTripEntity> activeTrips = factTripRepository.findByBusIdInAndStatusIn(busIds,
                List.of(TripStatus.IN_TRANSIT.name(), TripStatus.SCHEDULED.name()));
        Map<UUID, FactTripEntity> currentTrips = new HashMap<>();
        for (FactTripEntity trip : activeTrips) {
            if (trip.getDriverId() != null) {
                currentTrips.merge(trip.getBusId(), trip, this::closestTrip);
            }
        }
        return currentTrips.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getDriverId()));
    }

    private FactTripEntity closestTrip(FactTripEntity current, FactTripEntity candidate) {
        if (TripStatus.IN_TRANSIT.name().equals(current.getStatus())) {
            return current;
        }
        if (TripStatus.IN_TRANSIT.name().equals(candidate.getStatus())) {
            return candidate;
        }
        if (current.getScheduledDeparture() == null) {
            return candidate;
        }
        if (candidate.getScheduledDeparture() == null) {
            return current;
        }
        return candidate.getScheduledDeparture().isBefore(current.getScheduledDeparture()) ? candidate : current;
    }

    private Map<UUID, Long> counts(List<IdCountView> counts) {
        return counts.stream()
                .filter(count -> count.getId() != null)
                .collect(Collectors.toMap(IdCountView::getId, IdCountView::getTotal));
    }

    private Map<UUID, String> branchNames() {
        return dimBranchRepository.findAll().stream()
                .filter(branch -> branch.getName() != null)
                .collect(Collectors.toMap(DimBranchEntity::getBranchId, DimBranchEntity::getName));
    }

    private Map<UUID, String> plates(Collection<UUID> busIds) {
        return dimBusRepository.findAllById(busIds.stream().filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream()
                .filter(bus -> bus.getPlate() != null)
                .collect(Collectors.toMap(DimBusEntity::getBusId, DimBusEntity::getPlate));
    }

    private BigDecimal sum(Stream<BigDecimal> values) {
        return values.filter(Objects::nonNull).reduce(ZERO, BigDecimal::add);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE,
                    "La fecha final no puede ser anterior a la inicial");
        }
    }
}
