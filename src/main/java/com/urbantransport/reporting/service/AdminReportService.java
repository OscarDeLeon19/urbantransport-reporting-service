package com.urbantransport.reporting.service;

import com.urbantransport.reporting.dto.LedgerReportResponse;
import com.urbantransport.reporting.dto.ProfitReportResponse;
import com.urbantransport.reporting.dto.RouteMapResponse;
import com.urbantransport.reporting.dto.TopRoutesReportResponse;
import com.urbantransport.reporting.dto.WorkshopCategoryReportResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface AdminReportService {

    LedgerReportResponse income(LocalDate from, LocalDate to, UUID branchId);

    LedgerReportResponse expenses(LocalDate from, LocalDate to, UUID branchId);

    ProfitReportResponse profit(LocalDate from, LocalDate to, UUID branchId);

    TopRoutesReportResponse topRoutes(LocalDate from, LocalDate to);

    WorkshopCategoryReportResponse workshopByCategory(LocalDate from, LocalDate to, UUID branchId, UUID busId);

    RouteMapResponse routeMap(UUID branchId);
}
