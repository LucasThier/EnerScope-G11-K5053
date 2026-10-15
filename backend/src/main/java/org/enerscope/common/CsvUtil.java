package org.enerscope.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC-4180-style CSV reader/writer.
 *
 * <p>The project uses this helper for small CSV imports and exports. It
 * supports quoted fields, escaped quotes ({@code ""}) inside quotes, and both
 * {@code \n} and {@code \r\n} line endings.</p>
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
                        i++;
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
                case '\r' -> { /* handled by the following line feed */ }
                case '\n' -> {
                    current.add(field.toString().trim());
                    field.setLength(0);
                    addRow(rows, current);
                    current = new ArrayList<>();
                }
                default -> field.append(c);
            }
        }

        current.add(field.toString().trim());
        addRow(rows, current);
        return rows;
    }

    /**
     * Parses CSV while rejecting malformed quoting and enforcing a row limit.
     * Fully empty lines are skipped. The maximum includes the header row.
     */
    public static List<List<String>> parseStrict(String content, int maximumRows) {
        if (maximumRows < 1) {
            throw new IllegalArgumentException("Maximum CSV rows must be positive");
        }
        List<List<String>> rows = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return rows;
        }

        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean closedQuote = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == QUOTE) {
                    if (i + 1 < content.length() && content.charAt(i + 1) == QUOTE) {
                        field.append(QUOTE);
                        i++;
                    } else {
                        inQuotes = false;
                        closedQuote = true;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }

            if (closedQuote) {
                if (c == DELIMITER) {
                    current.add(field.toString().trim());
                    field.setLength(0);
                    closedQuote = false;
                } else if (c == '\n' || c == '\r') {
                    addStrictRow(rows, current, field, maximumRows);
                    current = new ArrayList<>();
                    field.setLength(0);
                    closedQuote = false;
                    if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                        i++;
                    }
                } else if (!Character.isWhitespace(c)) {
                    throw new IllegalArgumentException("Unexpected character after a quoted CSV field");
                }
                continue;
            }

            if (c == QUOTE) {
                if (!field.toString().isBlank()) {
                    throw new IllegalArgumentException("Quote inside an unquoted CSV field");
                }
                field.setLength(0);
                inQuotes = true;
            } else if (c == DELIMITER) {
                current.add(field.toString().trim());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                addStrictRow(rows, current, field, maximumRows);
                current = new ArrayList<>();
                field.setLength(0);
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                field.append(c);
            }
        }

        if (inQuotes) {
            throw new IllegalArgumentException("Unterminated quoted CSV field");
        }
        addStrictRow(rows, current, field, maximumRows);
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
        boolean allBlank = row.stream().allMatch(String::isEmpty);
        if (!allBlank) {
            rows.add(row);
        }
    }

    private static void addStrictRow(
            List<List<String>> rows,
            List<String> current,
            StringBuilder field,
            int maximumRows) {
        current.add(field.toString().trim());
        boolean allBlank = current.stream().allMatch(String::isEmpty);
        if (!allBlank) {
            if (rows.size() >= maximumRows) {
                throw new IllegalArgumentException(
                        "CSV exceeds the maximum of %d rows".formatted(maximumRows - 1));
            }
            rows.add(current);
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
