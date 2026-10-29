package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.domain.DimBranchEntity;
import com.urbantransport.reporting.domain.DimBusEntity;
import com.urbantransport.reporting.domain.DimRouteEntity;
import com.urbantransport.reporting.domain.FactTicketSaleEntity;
import com.urbantransport.reporting.domain.FactWorkshopLineEntity;
import com.urbantransport.reporting.domain.LedgerEntryEntity;
import com.urbantransport.reporting.domain.LedgerEntryType;
import com.urbantransport.reporting.dto.LedgerReportResponse;
import com.urbantransport.reporting.dto.ProfitReportResponse;
import com.urbantransport.reporting.dto.RouteMapResponse;
import com.urbantransport.reporting.dto.TopRoutesReportResponse;
import com.urbantransport.reporting.dto.WorkshopCategoryReportResponse;
import com.urbantransport.reporting.exception.ApiException;
import com.urbantransport.reporting.exception.ErrorCodes;
import com.urbantransport.reporting.repository.DimBranchRepository;
import com.urbantransport.reporting.repository.DimBusRepository;
import com.urbantransport.reporting.repository.DimRouteRepository;
import com.urbantransport.reporting.repository.FactTicketSaleRepository;
import com.urbantransport.reporting.repository.FactTicketSaleSpecifications;
import com.urbantransport.reporting.repository.FactWorkshopLineRepository;
import com.urbantransport.reporting.repository.FactWorkshopLineSpecifications;
import com.urbantransport.reporting.repository.LedgerEntryRepository;
import com.urbantransport.reporting.repository.LedgerEntrySpecifications;
import com.urbantransport.reporting.service.AdminReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminReportServiceImpl implements AdminReportService {

    private static final String NO_BRANCH = "Sin sucursal asignada";
    private static final String NO_ROUTES = "La sucursal no tiene rutas registradas";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final Locale SPANISH = Locale.of("es", "GT");

    private final LedgerEntryRepository ledgerEntryRepository;
    private final FactTicketSaleRepository factTicketSaleRepository;
    private final FactWorkshopLineRepository factWorkshopLineRepository;
    private final DimBranchRepository dimBranchRepository;
    private final DimRouteRepository dimRouteRepository;
    private final DimBusRepository dimBusRepository;

    @Override
    @Transactional(readOnly = true)
    public LedgerReportResponse income(LocalDate from, LocalDate to, UUID branchId) {
        return ledgerReport(LedgerEntryType.INCOME, from, to, branchId);
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerReportResponse expenses(LocalDate from, LocalDate to, UUID branchId) {
        return ledgerReport(LedgerEntryType.EXPENSE, from, to, branchId);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfitReportResponse profit(LocalDate from, LocalDate to, UUID branchId) {
        validateRange(from, to);
        List<LedgerEntryEntity> entries = ledgerEntryRepository.findAll(
                LedgerEntrySpecifications.filter(null, branchId, from, to));
        Map<UUID, String> branchNames = branchNames();

        Map<UUID, BigDecimal> income = new HashMap<>();
        Map<UUID, BigDecimal> expense = new HashMap<>();
        Set<UUID> branchIds = new HashSet<>(branchId != null ? Set.of(branchId) : branchNames.keySet());
        for (LedgerEntryEntity entry : entries) {
            branchIds.add(entry.getBranchId());
            Map<UUID, BigDecimal> target = entry.getType() == LedgerEntryType.INCOME ? income : expense;
            target.merge(entry.getBranchId(), entry.getAmount(), BigDecimal::add);
        }

        List<ProfitReportResponse.BranchProfit> branches = branchIds.stream()
                .map(id -> {
                    BigDecimal branchIncome = income.getOrDefault(id, ZERO);
                    BigDecimal branchExpense = expense.getOrDefault(id, ZERO);
                    return new ProfitReportResponse.BranchProfit(id, branchName(branchNames, id), branchIncome,
                            branchExpense, branchIncome.subtract(branchExpense));
                })
                .sorted(byBranchName(ProfitReportResponse.BranchProfit::getBranchId,
                        ProfitReportResponse.BranchProfit::getBranchName))
                .toList();

        BigDecimal totalIncome = sum(branches.stream().map(ProfitReportResponse.BranchProfit::getIncome));
        BigDecimal totalExpense = sum(branches.stream().map(ProfitReportResponse.BranchProfit::getExpense));
        return new ProfitReportResponse(branches, totalIncome, totalExpense, totalIncome.subtract(totalExpense));
    }

    @Override
    @Transactional(readOnly = true)
    public TopRoutesReportResponse topRoutes(LocalDate from, LocalDate to) {
        validateRange(from, to);
        Map<UUID, List<FactTicketSaleEntity>> salesByRoute = factTicketSaleRepository
                .findAll(FactTicketSaleSpecifications.activeSales(null, null, null, from, to))
                .stream()
                .filter(sale -> sale.getRouteId() != null)
                .collect(Collectors.groupingBy(FactTicketSaleEntity::getRouteId));
        Map<UUID, DimRouteEntity> routes = dimRouteRepository.findAllById(salesByRoute.keySet()).stream()
                .collect(Collectors.toMap(DimRouteEntity::getRouteId, Function.identity()));
        Map<UUID, String> branchNames = branchNames();

        List<TopRoutesReportResponse.RouteDemand> rows = salesByRoute.entrySet().stream()
                .map(entry -> {
                    DimRouteEntity route = routes.get(entry.getKey());
                    UUID originId = route != null ? route.getOriginBranchId() : null;
                    UUID destinationId = route != null ? route.getDestinationBranchId() : null;
                    List<FactTicketSaleEntity> sales = entry.getValue();
                    return new TopRoutesReportResponse.RouteDemand(
                            entry.getKey(),
                            originId,
                            originId == null ? null : branchName(branchNames, originId),
                            destinationId,
                            destinationId == null ? null : branchName(branchNames, destinationId),
                            route != null ? route.getDistanceKm() : null,
                            route != null ? route.getTicketPrice() : null,
                            sales.stream().map(FactTicketSaleEntity::getTripId).distinct().count(),
                            sales.size(),
                            sum(sales.stream().map(FactTicketSaleEntity::getPricePaid)));
                })
                .sorted(Comparator.comparingLong(TopRoutesReportResponse.RouteDemand::getTicketsSold).reversed()
                        .thenComparing(TopRoutesReportResponse.RouteDemand::getIncome, Comparator.reverseOrder()))
                .toList();

        return new TopRoutesReportResponse(rows,
                rows.stream().mapToLong(TopRoutesReportResponse.RouteDemand::getTicketsSold).sum(),
                sum(rows.stream().map(TopRoutesReportResponse.RouteDemand::getIncome)));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkshopCategoryReportResponse workshopByCategory(LocalDate from, LocalDate to, UUID branchId,
                                                             UUID busId) {
        validateRange(from, to);
        List<FactWorkshopLineEntity> lines = factWorkshopLineRepository.findAll(
                FactWorkshopLineSpecifications.filter(branchId, busId, from, to),
                Sort.by("serviceDate", "expenseId"));
        Map<UUID, String> plates = plates(lines.stream().map(FactWorkshopLineEntity::getBusId).toList());

        Map<UUID, List<FactWorkshopLineEntity>> byCategory = new HashMap<>();
        for (FactWorkshopLineEntity line : lines) {
            byCategory.computeIfAbsent(line.getCategoryId(), id -> new ArrayList<>()).add(line);
        }

        Collator collator = Collator.getInstance(SPANISH);
        List<WorkshopCategoryReportResponse.CategoryGroup> categories = byCategory.entrySet().stream()
                .map(entry -> new WorkshopCategoryReportResponse.CategoryGroup(
                        entry.getKey(),
                        entry.getValue().getFirst().getCategoryName(),
                        entry.getValue().stream()
                                .map(line -> new WorkshopCategoryReportResponse.Line(
                                        line.getLineId(),
                                        line.getExpenseId(),
                                        line.getBusId(),
                                        plates.get(line.getBusId()),
                                        line.getServiceDate(),
                                        line.getDescription(),
                                        line.getType(),
                                        line.getAmount()))
                                .toList(),
                        sum(entry.getValue().stream().map(FactWorkshopLineEntity::getAmount))))
                .sorted(Comparator.comparing(WorkshopCategoryReportResponse.CategoryGroup::getCategoryName,
                        Comparator.<String>nullsLast(collator)))
                .toList();

        return new WorkshopCategoryReportResponse(categories,
                sum(categories.stream().map(WorkshopCategoryReportResponse.CategoryGroup::getSubtotal)));
    }

    @Override
    @Transactional(readOnly = true)
    public RouteMapResponse routeMap(UUID branchId) {
        DimBranchEntity origin = dimBranchRepository.findById(branchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.BRANCH_NOT_FOUND,
                        "Sucursal no encontrada"));
        List<DimRouteEntity> routes = dimRouteRepository.findByOriginBranchId(branchId).stream()
                .filter(route -> !Boolean.TRUE.equals(route.getDeleted()))
                .toList();
        Map<UUID, DimBranchEntity> destinations = dimBranchRepository.findAllById(routes.stream()
                        .map(DimRouteEntity::getDestinationBranchId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(DimBranchEntity::getBranchId, Function.identity()));

        RouteMapResponse.MapPoint originPoint = mapPoint(branchId, origin);
        Collator collator = Collator.getInstance(SPANISH);
        List<RouteMapResponse.RouteLine> lines = routes.stream()
                .map(route -> new RouteMapResponse.RouteLine(
                        route.getRouteId(),
                        originPoint,
                        mapPoint(route.getDestinationBranchId(), destinations.get(route.getDestinationBranchId())),
                        route.getDistanceKm(),
                        route.getTicketPrice()))
                .sorted(Comparator.comparing((RouteMapResponse.RouteLine line) -> line.getDestination().getName(),
                        Comparator.<String>nullsLast(collator)))
                .toList();

        return new RouteMapResponse(branchId, origin.getName(), lines, lines.isEmpty() ? NO_ROUTES : null);
    }

    private LedgerReportResponse ledgerReport(LedgerEntryType type, LocalDate from, LocalDate to, UUID branchId) {
        validateRange(from, to);
        List<LedgerEntryEntity> entries = ledgerEntryRepository.findAll(
                LedgerEntrySpecifications.filter(type, branchId, from, to),
                Sort.by("occurredOn", "createdAt"));
        Map<UUID, String> branchNames = branchNames();

        Map<UUID, List<LedgerEntryEntity>> byBranch = new HashMap<>();
        for (LedgerEntryEntity entry : entries) {
            byBranch.computeIfAbsent(entry.getBranchId(), id -> new ArrayList<>()).add(entry);
        }

        List<LedgerReportResponse.BranchGroup> branches = byBranch.entrySet().stream()
                .map(entry -> new LedgerReportResponse.BranchGroup(
                        entry.getKey(),
                        branchName(branchNames, entry.getKey()),
                        entry.getValue().stream().map(LedgerReportResponse.Entry::fromEntity).toList(),
                        sum(entry.getValue().stream().map(LedgerEntryEntity::getAmount))))
                .sorted(byBranchName(LedgerReportResponse.BranchGroup::getBranchId,
                        LedgerReportResponse.BranchGroup::getBranchName))
                .toList();

        return new LedgerReportResponse(type, branches, sum(entries.stream().map(LedgerEntryEntity::getAmount)));
    }

    private RouteMapResponse.MapPoint mapPoint(UUID branchId, DimBranchEntity branch) {
        if (branch == null) {
            return new RouteMapResponse.MapPoint(branchId, null, null, null);
        }
        return new RouteMapResponse.MapPoint(branchId, branch.getName(), branch.getLatitude(), branch.getLongitude());
    }

    private Map<UUID, String> branchNames() {
        return dimBranchRepository.findAll().stream()
                .collect(Collectors.toMap(DimBranchEntity::getBranchId,
                        branch -> branch.getName() != null ? branch.getName() : branch.getBranchId().toString()));
    }

    private String branchName(Map<UUID, String> branchNames, UUID branchId) {
        if (branchId == null) {
            return NO_BRANCH;
        }
        return branchNames.getOrDefault(branchId, branchId.toString());
    }

    private Map<UUID, String> plates(Collection<UUID> busIds) {
        return dimBusRepository.findAllById(busIds.stream().filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream()
                .filter(bus -> bus.getPlate() != null)
                .collect(Collectors.toMap(DimBusEntity::getBusId, DimBusEntity::getPlate));
    }

    private <T> Comparator<T> byBranchName(Function<T, UUID> branchId, Function<T, String> branchName) {
        Collator collator = Collator.getInstance(SPANISH);
        return Comparator.comparing((T item) -> branchId.apply(item) == null)
                .thenComparing(branchName, collator);
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
