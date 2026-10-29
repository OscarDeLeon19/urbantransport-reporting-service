package com.urbantransport.reporting.service;

import com.urbantransport.reporting.domain.BusStatus;
import com.urbantransport.reporting.dto.BusDepreciationReportResponse;
import com.urbantransport.reporting.dto.BusListReportResponse;
import com.urbantransport.reporting.dto.DriverListReportResponse;
import com.urbantransport.reporting.dto.LedgerReportResponse;
import com.urbantransport.reporting.dto.ProfitReportResponse;
import com.urbantransport.reporting.dto.RentalReportResponse;
import com.urbantransport.reporting.dto.RouteMapResponse;
import com.urbantransport.reporting.dto.TicketSalesReportResponse;
import com.urbantransport.reporting.dto.TopRoutesReportResponse;
import com.urbantransport.reporting.dto.WorkshopBusReportResponse;
import com.urbantransport.reporting.dto.WorkshopCategoryReportResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface ReportPdfService {

    byte[] income(LedgerReportResponse report, LocalDate from, LocalDate to, UUID branchId);

    byte[] expenses(LedgerReportResponse report, LocalDate from, LocalDate to, UUID branchId);

    byte[] profit(ProfitReportResponse report, LocalDate from, LocalDate to, UUID branchId);

    byte[] topRoutes(TopRoutesReportResponse report, LocalDate from, LocalDate to);

    byte[] workshopByCategory(WorkshopCategoryReportResponse report, LocalDate from, LocalDate to, UUID branchId,
                              UUID busId);

    byte[] routeMap(RouteMapResponse report);

    byte[] buses(BusListReportResponse report, UUID branchId, BusStatus status);

    byte[] drivers(DriverListReportResponse report, UUID branchId);

    byte[] ticketSales(TicketSalesReportResponse report, UUID branchId, LocalDate from, LocalDate to, UUID routeId,
                       UUID busId);

    byte[] rentals(RentalReportResponse report, UUID branchId, LocalDate from, LocalDate to);

    byte[] busDepreciation(BusDepreciationReportResponse report, UUID branchId, LocalDate from, LocalDate to);

    byte[] workshopByBus(WorkshopBusReportResponse report, UUID branchId, LocalDate from, LocalDate to);
}
