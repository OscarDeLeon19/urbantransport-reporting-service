package com.urbantransport.reporting.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfReportRendererTest {

    private static final List<ReportDocument.Column> COLUMNS = List.of(
            ReportDocument.Column.text("Concepto", 4),
            ReportDocument.Column.number("Monto", 2));

    private final PdfReportRenderer renderer = new PdfReportRenderer();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(renderer, "zone", ZoneId.of("America/Guatemala"));
    }

    @Test
    void fullReport_hasHeaderFiltersSectionsSubtotalsAndTotals() throws IOException {
        ReportDocument document = new ReportDocument("Reporte de ingresos",
                List.of(new ReportDocument.Filter("Sucursal", "Xela")),
                List.of(
                        new ReportDocument.Section("Sucursal Xela", COLUMNS,
                                List.of(List.of("Boletos", "Q 100.00"), List.of("Alquiler", "Q 250.00")),
                                "Subtotal Xela", "Q 350.00"),
                        ReportDocument.Section.of(null, COLUMNS, List.of())),
                List.of(new ReportDocument.Total("Total global", "Q 350.00")));

        byte[] pdf = renderer.render(document);

        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        String text = text(pdf, 1);
        assertTrue(text.contains("UrbanTransport"));
        assertTrue(text.contains("Reporte de ingresos"));
        assertTrue(text.contains("Generado el"));
        assertTrue(text.contains("Filtros aplicados"));
        assertTrue(text.contains("Sucursal: Xela"));
        assertTrue(text.contains("Sucursal Xela"));
        assertTrue(text.contains("Boletos"));
        assertTrue(text.contains("Q 250.00"));
        assertTrue(text.contains("Subtotal Xela"));
        assertTrue(text.contains("Sin registros"));
        assertTrue(text.contains("Total global"));
    }

    @Test
    void reportWithoutRows_showsNoRecordsInsteadOfTablesAndTotals() throws IOException {
        ReportDocument document = new ReportDocument("Reporte de gastos",
                List.of(new ReportDocument.Filter("Desde", "Sin límite")),
                List.of(ReportDocument.Section.of("Xela", COLUMNS, List.of())),
                List.of(new ReportDocument.Total("Total global", "Q 0.00")));

        byte[] pdf = renderer.render(document);

        String text = text(pdf, 1);
        assertTrue(text.contains("Sin registros"));
        assertFalse(text.contains("Total global"));
        assertFalse(text.contains("Concepto"));
    }

    @Test
    void reportWithoutFiltersOrTotals_omitsThoseBlocks() throws IOException {
        ReportDocument document = new ReportDocument("Mapa de rutas", List.of(),
                List.of(ReportDocument.Section.of(null, COLUMNS, List.of(List.of("Ruta", "Q 10.00")))),
                List.of());

        String text = text(renderer.render(document), 1);

        assertFalse(text.contains("Filtros aplicados"));
        assertTrue(text.contains("Ruta"));
    }

    @Test
    void longReport_spansSeveralPagesWithFooter() throws IOException {
        List<List<String>> rows = new ArrayList<>();
        IntStream.range(0, 120).forEach(i -> rows.add(List.of("Fila " + i, "Q " + i + ".00")));
        ReportDocument document = new ReportDocument("Listado", List.of(),
                List.of(ReportDocument.Section.of(null, COLUMNS, rows)), List.of());

        byte[] pdf = renderer.render(document);

        PdfReader reader = new PdfReader(pdf);
        assertTrue(reader.getNumberOfPages() > 1);
        assertTrue(text(pdf, 2).contains("2"));
        assertTrue(text(pdf, reader.getNumberOfPages()).contains("Fila 119"));
        reader.close();
    }

    private static String text(byte[] pdf, int page) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(page);
        } finally {
            reader.close();
        }
    }
}
