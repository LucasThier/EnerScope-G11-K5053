package org.enerscope.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC-4180-style CSV reader/writer.
 *
 * <p>The project only needs to parse small credential lists and emit a
 * credentials file, so a self-contained helper is preferred over pulling in a
 * dedicated CSV dependency. It supports quoted fields, escaped quotes
 * ({@code ""}) inside quotes, and both {@code \n} and {@code \r\n} line
 * endings. It does not stream: the whole document is read into memory, which is
 * fine for the expected input sizes.</p>
 */
public final class CsvUtil {

    private static final char DELIMITER = ',';
    private static final char QUOTE = '"';

    private CsvUtil() {}

    /**
     * Parses CSV text into a list of rows, each row a list of trimmed fields.
     * Fully empty lines are skipped.
     */
    public static List<List<String>> parse(String content) {
        List<List<String>> rows = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return rows;
        }

        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (inQuotes) {
                if (c == QUOTE) {
                    if (i + 1 < content.length() && content.charAt(i + 1) == QUOTE) {
                        field.append(QUOTE);
                        i++; // consume the escaped quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }

            switch (c) {
                case QUOTE -> inQuotes = true;
                case DELIMITER -> {
                    current.add(field.toString().trim());
                    field.setLength(0);
                }
                case '\r' -> { /* ignore; handled by \n */ }
                case '\n' -> {
                    current.add(field.toString().trim());
                    field.setLength(0);
                    addRow(rows, current);
                    current = new ArrayList<>();
                }
                default -> field.append(c);
            }
        }

        // Flush the last field/row (files without a trailing newline).
        current.add(field.toString().trim());
        addRow(rows, current);

        return rows;
    }

    /** Serialises rows to CSV text, quoting fields only when necessary. */
    public static String write(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    sb.append(DELIMITER);
                }
                sb.append(escape(row.get(i)));
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        // Drop rows that are entirely empty (e.g. a blank trailing line).
        boolean allBlank = row.stream().allMatch(String::isEmpty);
        if (!allBlank) {
            rows.add(row);
        }
    }

    private static String escape(String value) {
        String v = value == null ? "" : value;
        boolean mustQuote = v.indexOf(DELIMITER) >= 0
                || v.indexOf(QUOTE) >= 0
                || v.indexOf('\n') >= 0
                || v.indexOf('\r') >= 0;
        if (!mustQuote) {
            return v;
        }
        return QUOTE + v.replace("\"", "\"\"") + QUOTE;
    }
}
