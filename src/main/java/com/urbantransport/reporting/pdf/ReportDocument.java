package com.urbantransport.reporting.pdf;

import java.util.List;

public record ReportDocument(String title, List<Filter> filters, List<Section> sections, List<Total> totals) {

    public boolean isEmpty() {
        return sections.stream().allMatch(section -> section.rows().isEmpty());
    }

    public record Filter(String label, String value) {
    }

    public record Column(String header, float width, boolean numeric) {

        public static Column text(String header, float width) {
            return new Column(header, width, false);
        }

        public static Column number(String header, float width) {
            return new Column(header, width, true);
        }
    }

    public record Section(String title, List<Column> columns, List<List<String>> rows, String subtotalLabel,
                          String subtotalValue) {

        public static Section of(String title, List<Column> columns, List<List<String>> rows) {
            return new Section(title, columns, rows, null, null);
        }
    }

    public record Total(String label, String value) {
    }
}
