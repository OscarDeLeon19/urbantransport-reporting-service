package com.urbantransport.reporting.service;

import com.urbantransport.reporting.domain.BusStatus;
import com.urbantransport.reporting.dto.BusDepreciationReportResponse;
import com.urbantransport.reporting.dto.BusListReportResponse;
import com.urbantransport.reporting.dto.DriverListReportResponse;
import com.urbantransport.reporting.dto.RentalReportResponse;
import com.urbantransport.reporting.dto.TicketSalesReportResponse;
import com.urbantransport.reporting.dto.WorkshopBusReportResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface BranchReportService {

    BusListReportResponse buses(UUID branchId, BusStatus status);

    DriverListReportResponse drivers(UUID branchId);

    TicketSalesReportResponse ticketSales(UUID branchId, LocalDate from, LocalDate to, UUID routeId, UUID busId);

    RentalReportResponse rentals(UUID branchId, LocalDate from, LocalDate to);

    BusDepreciationReportResponse busDepreciation(UUID branchId, LocalDate from, LocalDate to);

    WorkshopBusReportResponse workshopByBus(UUID branchId, LocalDate from, LocalDate to);
}
