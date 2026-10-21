package com.example.library.export;

import javax.swing.table.TableModel;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Exports the displayed rows as UTF-8 CSV, with Persian text support in Excel. */
public final class CsvExporter {
    private CsvExporter() { }

    public static void write(TableModel table, Path destination) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            for (int column = 0; column < table.getColumnCount(); column++) {
                if (column > 0) writer.write(',');
                writer.write(cell(table.getColumnName(column)));
            }
            writer.write("\r\n");
            for (int row = 0; row < table.getRowCount(); row++) {
                for (int column = 0; column < table.getColumnCount(); column++) {
                    if (column > 0) writer.write(',');
                    writer.write(cell(table.getValueAt(row, column)));
                }
                writer.write("\r\n");
            }
        }
    }

    private static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        // Spreadsheet applications must treat untrusted text as data, never formulas.
        if (value instanceof String && !text.isEmpty()
                && "=+-@\t\r\n".indexOf(text.stripLeading().isEmpty()
                        ? text.charAt(0) : text.stripLeading().charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
