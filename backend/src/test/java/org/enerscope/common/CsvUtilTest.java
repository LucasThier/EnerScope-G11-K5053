package org.enerscope.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvUtilTest {

    @Test
    void parsesSimpleRowsAndTrims() {
        List<List<String>> rows = CsvUtil.parse("mail, firstName ,lastName\na@b.com,Jane,Doe\n");
        assertEquals(2, rows.size());
        assertEquals(List.of("mail", "firstName", "lastName"), rows.get(0));
        assertEquals(List.of("a@b.com", "Jane", "Doe"), rows.get(1));
    }

    @Test
    void handlesQuotedFieldsWithCommasAndEscapedQuotes() {
        List<List<String>> rows = CsvUtil.parse("name\n\"Doe, Jr.\"\n\"She said \"\"hi\"\"\"\n");
        assertEquals("Doe, Jr.", rows.get(1).get(0));
        assertEquals("She said \"hi\"", rows.get(2).get(0));
    }

    @Test
    void skipsBlankLinesAndHandlesCrlfAndMissingTrailingNewline() {
        List<List<String>> rows = CsvUtil.parse("a,b\r\n\r\nc,d");
        assertEquals(2, rows.size());
        assertEquals(List.of("a", "b"), rows.get(0));
        assertEquals(List.of("c", "d"), rows.get(1));
    }

    @Test
    void parseEmptyContentReturnsNoRows() {
        assertTrue(CsvUtil.parse("").isEmpty());
        assertTrue(CsvUtil.parse(null).isEmpty());
    }

    @Test
    void writeQuotesOnlyWhenNecessaryAndRoundTrips() {
        String csv = CsvUtil.write(List.of(
                List.of("mail", "password"),
                List.of("a@b.com", "pa,ss\"word")));
        assertEquals("mail,password\r\na@b.com,\"pa,ss\"\"word\"\r\n", csv);

        List<List<String>> reparsed = CsvUtil.parse(csv);
        assertEquals("pa,ss\"word", reparsed.get(1).get(1));
    }

    @Test
    void strictParserRejectsUnterminatedQuotedFields() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> CsvUtil.parseStrict("name\n\"unterminated", 10))
                .getMessage().contains("Unterminated quoted CSV field"));
    }

    @Test
    void strictParserRejectsQuotesInsideUnquotedFields() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> CsvUtil.parseStrict("name\nSample\"bad", 10))
                .getMessage().contains("Quote inside an unquoted CSV field"));
    }

    @Test
    void strictParserSupportsMultilineQuotedFieldsAndEnforcesRowLimit() {
        List<List<String>> rows = CsvUtil.parseStrict("name\r\n\"North\nwell\"\r\n", 2);
        assertEquals(List.of(List.of("name"), List.of("North\nwell")), rows);

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> CsvUtil.parseStrict("name\none\ntwo\n", 2))
                .getMessage().contains("maximum of 1 rows"));
    }
}
