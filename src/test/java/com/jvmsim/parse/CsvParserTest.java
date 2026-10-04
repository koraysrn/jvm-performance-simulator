package com.jvmsim.parse;

import com.jvmsim.model.LogRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvParserTest {

    private static LogRecord parse(String line) {
        return new CsvParser(MalformedLinePolicy.SKIP).parseLine(line, 1);
    }

    @Test
    void parsesValidLine() {
        LogRecord record = parse("2026-10-04T20:00:00Z,INFO,auth-service,login ok,42.5");
        assertEquals("2026-10-04T20:00:00Z", record.timestamp());
        assertEquals("INFO", record.level());
        assertEquals("auth-service", record.source());
        assertEquals("login ok", record.message());
        assertEquals(42.5, record.value());
    }

    @Test
    void blankLineReturnsNullAndIsNotCountedAsMalformed() {
        CsvParser parser = new CsvParser(MalformedLinePolicy.SKIP);
        assertNull(parser.parseLine("", 5));
        assertEquals(0, parser.malformedCount());
    }

    @Test
    void quotedFieldWithEmbeddedCommaIsDecoded() {
        LogRecord record = parse("ts,WARN,gateway,\"hello, world\",1.0");
        assertEquals("hello, world", record.message());
    }

    @Test
    void escapedQuotesAreDecoded() {
        LogRecord record = parse("ts,INFO,s,\"say \"\"hi\"\"\",1.0");
        assertEquals("say \"hi\"", record.message());
    }

    @Test
    void surroundingSpacesAreTrimmed() {
        LogRecord record = parse(" ts , INFO , s , m , 1.0 ");
        assertEquals("ts", record.timestamp());
        assertEquals("INFO", record.level());
        assertEquals(1.0, record.value());
    }

    @Test
    void skipPolicyDropsWrongColumnCountAndCountsIt() {
        CsvParser parser = new CsvParser(MalformedLinePolicy.SKIP);
        assertNull(parser.parseLine("ts,INFO,s,m", 1));
        assertNull(parser.parseLine("ts,INFO,s,m,1.0,extra", 2));
        assertEquals(2, parser.malformedCount());
    }

    @Test
    void skipPolicyDropsNonNumericValueAndCountsIt() {
        CsvParser parser = new CsvParser(MalformedLinePolicy.SKIP);
        assertNull(parser.parseLine("ts,INFO,s,m,not-a-number", 1));
        assertEquals(1, parser.malformedCount());
    }

    @Test
    void failFastThrowsOnWrongColumnCount() {
        CsvParser parser = new CsvParser(MalformedLinePolicy.FAIL_FAST);
        MalformedLineException ex = assertThrows(MalformedLineException.class,
                () -> parser.parseLine("ts,INFO,s,m", 7));
        assertEquals(true, ex.getMessage().contains("line 7"));
    }

    @Test
    void failFastThrowsOnNonNumericValue() {
        CsvParser parser = new CsvParser(MalformedLinePolicy.FAIL_FAST);
        assertThrows(MalformedLineException.class,
                () -> parser.parseLine("ts,INFO,s,m,abc", 2));
    }

    @Test
    void stripsUtf8BomOnFirstLineOnly() {
        CsvParser parser = new CsvParser(MalformedLinePolicy.SKIP);
        LogRecord first = parser.parseLine("\uFEFFts,INFO,s,m,1.0", 1);
        assertEquals("ts", first.timestamp());
        LogRecord second = parser.parseLine("\uFEFFts,INFO,s,m,1.0", 2);
        assertEquals("\uFEFFts", second.timestamp());
    }

    @Test
    void parseChunkDecodesAllNonBlankLinesWithSingleParser() {
        CsvParser parser = new CsvParser(MalformedLinePolicy.SKIP);
        List<LogRecord> records = parser.parseChunk(
                List.of("t1,INFO,s,m,1.0", "", "t3,WARN,s,m,3.0"), 1);

        assertEquals(2, records.size());
        assertEquals("t1", records.get(0).timestamp());
        assertEquals("t3", records.get(1).timestamp());
        assertEquals(0, parser.malformedCount());
    }

    @Test
    void parseChunkSkipsAndCountsMalformedLines() {
        CsvParser parser = new CsvParser(MalformedLinePolicy.SKIP);
        List<LogRecord> records = parser.parseChunk(
                List.of("ok,INFO,s,m,1.0", "broken,line", "also,INFO,s,m,bad"), 1);

        assertEquals(1, records.size());
        assertEquals(2, parser.malformedCount());
    }

    @Test
    void failFastMessageIsSanitizedAgainstControlCharacters() {
        CsvParser parser = new CsvParser(MalformedLinePolicy.FAIL_FAST);
        MalformedLineException ex = assertThrows(MalformedLineException.class,
                () -> parser.parseLine("ts,INFO,s,m,bad\r\ninjected", 1));

        assertFalse(ex.getMessage().contains("\n"));
        assertFalse(ex.getMessage().contains("\r"));
    }

    @Test
    void handlesTenMegabyteSingleLine() {
        CsvParser parser = new CsvParser(MalformedLinePolicy.SKIP);
        String hugeMessage = "a".repeat(10 * 1024 * 1024);

        List<LogRecord> records = parser.parseChunk(
                List.of("ts,INFO,s," + hugeMessage + ",1.0"), 1);

        assertEquals(1, records.size());
        assertEquals(hugeMessage, records.get(0).message());
    }
}
