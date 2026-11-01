package com.urbantransport.reporting.web;

import com.urbantransport.reporting.dto.LedgerReportResponse;
import com.urbantransport.reporting.dto.ProfitReportResponse;
import com.urbantransport.reporting.dto.ReportFormat;
import com.urbantransport.reporting.dto.RouteMapResponse;
import com.urbantransport.reporting.dto.TopRoutesReportResponse;
import com.urbantransport.reporting.dto.WorkshopCategoryReportResponse;
import com.urbantransport.reporting.service.AdminReportService;
import com.urbantransport.reporting.service.ReportPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
public class AdminReportController {

    private final AdminReportService adminReportService;
    private final ReportPdfService reportPdfService;

    @GetMapping("/income")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> income(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(required = false) UUID branchId,
                                    @RequestParam(defaultValue = "json") String format) {
        ReportFormat reportFormat = ReportFormat.from(format);
        LedgerReportResponse report = adminReportService.income(from, to, branchId);
        return respond(reportFormat, report, () -> reportPdfService.income(report, from, to, branchId),
                "reporte-ingresos");
    }

    @GetMapping("/expenses")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> expenses(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) UUID branchId,
                                      @RequestParam(defaultValue = "json") String format) {
        ReportFormat reportFormat = ReportFormat.from(format);
        LedgerReportResponse report = adminReportService.expenses(from, to, branchId);
        return respond(reportFormat, report, () -> reportPdfService.expenses(report, from, to, branchId),
                "reporte-gastos");
    }

    @GetMapping("/profit")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> profit(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(required = false) UUID branchId,
                                    @RequestParam(defaultValue = "json") String format) {
        ReportFormat reportFormat = ReportFormat.from(format);
        ProfitReportResponse report = adminReportService.profit(from, to, branchId);
        return respond(reportFormat, report, () -> reportPdfService.profit(report, from, to, branchId),
                "reporte-ganancias");
    }

    @GetMapping("/top-routes")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> topRoutes(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(defaultValue = "json") String format) {
        ReportFormat reportFormat = ReportFormat.from(format);
        TopRoutesReportResponse report = adminReportService.topRoutes(from, to);
        return respond(reportFormat, report, () -> reportPdfService.topRoutes(report, from, to),
                "reporte-rutas-mas-demandadas");
    }

    @GetMapping("/workshop-by-category")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> workshopByCategory(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(required = false) UUID branchId,
                                                @RequestParam(required = false) UUID busId,
                                                @RequestParam(defaultValue = "json") String format) {
        ReportFormat reportFormat = ReportFormat.from(format);
        WorkshopCategoryReportResponse report = adminReportService.workshopByCategory(from, to, branchId, busId);
        return respond(reportFormat, report,
                () -> reportPdfService.workshopByCategory(report, from, to, branchId, busId),
                "reporte-taller-por-categoria");
    }

    @GetMapping("/route-map")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> routeMap(@RequestParam UUID branchId,
                                      @RequestParam(defaultValue = "json") String format) {
        ReportFormat reportFormat = ReportFormat.from(format);
        RouteMapResponse report = adminReportService.routeMap(branchId);
        return respond(reportFormat, report, () -> reportPdfService.routeMap(report), "mapa-de-rutas");
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
}
