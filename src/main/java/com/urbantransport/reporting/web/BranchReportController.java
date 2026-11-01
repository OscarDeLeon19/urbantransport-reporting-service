package com.urbantransport.reporting.web;

import com.urbantransport.reporting.domain.BusStatus;
import com.urbantransport.reporting.dto.BusDepreciationReportResponse;
import com.urbantransport.reporting.dto.BusListReportResponse;
import com.urbantransport.reporting.dto.DriverListReportResponse;
import com.urbantransport.reporting.dto.RentalReportResponse;
import com.urbantransport.reporting.dto.ReportFormat;
import com.urbantransport.reporting.dto.TicketSalesReportResponse;
import com.urbantransport.reporting.dto.WorkshopBusReportResponse;
import com.urbantransport.reporting.exception.ApiException;
import com.urbantransport.reporting.exception.ErrorCodes;
import com.urbantransport.reporting.service.BranchReportService;
import com.urbantransport.reporting.service.ReportPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class BranchReportController {

    private final BranchReportService branchReportService;
    private final ReportPdfService reportPdfService;

    @GetMapping("/buses")
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public ResponseEntity<?> buses(@RequestParam(required = false) BusStatus status,
                                   @RequestParam(defaultValue = "json") String format,
                                   @AuthenticationPrincipal Jwt jwt) {
        ReportFormat reportFormat = ReportFormat.from(format);
        UUID branchId = branchIdOf(jwt);
        BusListReportResponse report = branchReportService.buses(branchId, status);
        return respond(reportFormat, report, () -> reportPdfService.buses(report, branchId, status),
                "listado-de-buses");
    }

    @GetMapping("/drivers")
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public ResponseEntity<?> drivers(@RequestParam(defaultValue = "json") String format,
                                     @AuthenticationPrincipal Jwt jwt) {
        ReportFormat reportFormat = ReportFormat.from(format);
        UUID branchId = branchIdOf(jwt);
        DriverListReportResponse report = branchReportService.drivers(branchId);
        return respond(reportFormat, report, () -> reportPdfService.drivers(report, branchId),
                "listado-de-choferes");
    }

    @GetMapping("/ticket-sales")
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public ResponseEntity<?> ticketSales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) UUID routeId,
                                         @RequestParam(required = false) UUID busId,
                                         @RequestParam(defaultValue = "json") String format,
                                         @AuthenticationPrincipal Jwt jwt) {
        ReportFormat reportFormat = ReportFormat.from(format);
        UUID branchId = branchIdOf(jwt);
        TicketSalesReportResponse report = branchReportService.ticketSales(branchId, from, to, routeId, busId);
        return respond(reportFormat, report,
                () -> reportPdfService.ticketSales(report, branchId, from, to, routeId, busId),
                "ingresos-por-boletos");
    }

    @GetMapping("/rentals")
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public ResponseEntity<?> rentals(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(defaultValue = "json") String format,
                                     @AuthenticationPrincipal Jwt jwt) {
        ReportFormat reportFormat = ReportFormat.from(format);
        UUID branchId = branchIdOf(jwt);
        RentalReportResponse report = branchReportService.rentals(branchId, from, to);
        return respond(reportFormat, report, () -> reportPdfService.rentals(report, branchId, from, to),
                "ingresos-por-alquiler");
    }

    @GetMapping("/bus-depreciation")
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public ResponseEntity<?> busDepreciation(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(defaultValue = "json") String format,
                                             @AuthenticationPrincipal Jwt jwt) {
        ReportFormat reportFormat = ReportFormat.from(format);
        UUID branchId = branchIdOf(jwt);
        BusDepreciationReportResponse report = branchReportService.busDepreciation(branchId, from, to);
        return respond(reportFormat, report, () -> reportPdfService.busDepreciation(report, branchId, from, to),
                "depreciacion-por-bus");
    }

    @GetMapping("/workshop-by-bus")
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public ResponseEntity<?> workshopByBus(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "json") String format,
                                           @AuthenticationPrincipal Jwt jwt) {
        ReportFormat reportFormat = ReportFormat.from(format);
        UUID branchId = branchIdOf(jwt);
        WorkshopBusReportResponse report = branchReportService.workshopByBus(branchId, from, to);
        return respond(reportFormat, report, () -> reportPdfService.workshopByBus(report, branchId, from, to),
                "taller-por-bus");
    }

    private ResponseEntity<?> respond(ReportFormat format, Object report, Supplier<byte[]> pdf, String fileName) {
        if (format == ReportFormat.PDF) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(fileName + ".pdf").build().toString())
                    .body(pdf.get());
        }
        return ResponseEntity.ok(report);
    }

    private UUID branchIdOf(Jwt jwt) {
        String branchId = jwt.getClaimAsString("branchId");
        if (branchId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.BRANCH_REQUIRED, "El usuario no tiene sucursal asignada");
        }
        return UUID.fromString(branchId);
    }
}
