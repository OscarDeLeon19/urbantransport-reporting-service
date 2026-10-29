package com.urbantransport.reporting.pdf;

import com.urbantransport.reporting.exception.ApiException;
import com.urbantransport.reporting.exception.ErrorCodes;
import lombok.extern.slf4j.Slf4j;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class PdfReportRenderer {

    private static final String SYSTEM_NAME = "UrbanTransport";
    private static final String NO_RECORDS = "Sin registros";
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Color PRIMARY = new Color(31, 78, 121);
    private static final Color SUBTOTAL_BACKGROUND = new Color(234, 239, 244);
    private static final Color BORDER = new Color(200, 205, 210);
    private static final Color MUTED = new Color(110, 110, 110);

    private static final Font SYSTEM_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, MUTED);
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD, PRIMARY);
    private static final Font META_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED);
    private static final Font FILTER_LABEL_FONT = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font FILTER_VALUE_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, PRIMARY);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font TOTAL_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font EMPTY_FONT = new Font(Font.HELVETICA, 11, Font.ITALIC, MUTED);

    @Value("${app.zone:America/Guatemala}")
    private ZoneId zone;

    public byte[] render(ReportDocument report) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 48);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new PageFooter());
            document.open();

            addHeader(document, report.title());
            addFilters(document, report.filters());
            if (report.isEmpty()) {
                Paragraph empty = new Paragraph(NO_RECORDS, EMPTY_FONT);
                empty.setSpacingBefore(16);
                document.add(empty);
            } else {
                for (ReportDocument.Section section : report.sections()) {
                    addSection(document, section);
                }
                addTotals(document, report.totals());
            }
        } catch (DocumentException exception) {
            log.error("No se pudo generar el PDF {}", report.title(), exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.PDF_GENERATION_FAILED,
                    "No se pudo generar el PDF");
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return output.toByteArray();
    }

    private void addHeader(Document document, String title) throws DocumentException {
        document.add(new Paragraph(SYSTEM_NAME, SYSTEM_FONT));
        document.add(new Paragraph(title, TITLE_FONT));
        Paragraph generated = new Paragraph("Generado el " + ZonedDateTime.now(zone).format(GENERATED_AT), META_FONT);
        generated.setSpacingAfter(8);
        document.add(generated);
    }

    private void addFilters(Document document, List<ReportDocument.Filter> filters) throws DocumentException {
        if (filters.isEmpty()) {
            return;
        }
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk("Filtros aplicados", FILTER_LABEL_FONT));
        document.add(paragraph);
        for (ReportDocument.Filter filter : filters) {
            Paragraph line = new Paragraph();
            line.add(new Chunk(filter.label() + ": ", FILTER_LABEL_FONT));
            line.add(new Chunk(filter.value(), FILTER_VALUE_FONT));
            document.add(line);
        }
    }

    private void addSection(Document document, ReportDocument.Section section) throws DocumentException {
        if (section.title() != null) {
            Paragraph title = new Paragraph(section.title(), SECTION_FONT);
            title.setSpacingBefore(12);
            title.setSpacingAfter(4);
            document.add(title);
        }

        List<ReportDocument.Column> columns = section.columns();
        float[] widths = new float[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            widths[i] = columns.get(i).width();
        }
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        if (section.title() == null) {
            table.setSpacingBefore(12);
        }

        for (ReportDocument.Column column : columns) {
            PdfPCell header = cell(column.header(), column.numeric(), HEADER_FONT);
            header.setBackgroundColor(PRIMARY);
            table.addCell(header);
        }

        if (section.rows().isEmpty()) {
            PdfPCell empty = cell(NO_RECORDS, false, EMPTY_FONT);
            empty.setColspan(columns.size());
            table.addCell(empty);
        }
        for (List<String> row : section.rows()) {
            for (int i = 0; i < columns.size(); i++) {
                table.addCell(cell(row.get(i), columns.get(i).numeric(), BODY_FONT));
            }
        }

        if (section.subtotalLabel() != null) {
            PdfPCell label = cell(section.subtotalLabel(), false, BOLD_FONT);
            label.setColspan(columns.size() - 1);
            label.setBackgroundColor(SUBTOTAL_BACKGROUND);
            table.addCell(label);
            PdfPCell value = cell(section.subtotalValue(), true, BOLD_FONT);
            value.setBackgroundColor(SUBTOTAL_BACKGROUND);
            table.addCell(value);
        }

        document.add(table);
    }

    private void addTotals(Document document, List<ReportDocument.Total> totals) throws DocumentException {
        if (totals.isEmpty()) {
            return;
        }
        PdfPTable table = new PdfPTable(new float[]{3, 2});
        table.setWidthPercentage(40);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setSpacingBefore(14);
        for (ReportDocument.Total total : totals) {
            PdfPCell label = cell(total.label(), false, TOTAL_FONT);
            label.setBackgroundColor(SUBTOTAL_BACKGROUND);
            table.addCell(label);
            PdfPCell value = cell(total.value(), true, TOTAL_FONT);
            value.setBackgroundColor(SUBTOTAL_BACKGROUND);
            table.addCell(value);
        }
        document.add(table);
    }

    private PdfPCell cell(String text, boolean numeric, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(4);
        cell.setHorizontalAlignment(numeric ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        cell.setBorderColor(BORDER);
        return cell;
    }

    private static class PageFooter extends PdfPageEventHelper {

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase("Página " + writer.getPageNumber(), META_FONT),
                    document.right(), document.bottom() - 20, 0);
        }
    }
}
