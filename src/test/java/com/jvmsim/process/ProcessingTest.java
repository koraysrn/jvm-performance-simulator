package com.jvmsim.process;

import com.jvmsim.model.AggregationResult;
import com.jvmsim.model.LogRecord;
import com.jvmsim.model.ProcessedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingTest {

    private static LogRecord rec(String level, String source, double value) {
        return new LogRecord("ts", level, source, "msg", value);
    }

    @Test
    void filterLevelMatchesOnlyListedLevels() {
        LogRecordFilter filter = Filters.level("ERROR", "WARN");
        assertTrue(filter.test(rec("ERROR", "s", 1.0)));
        assertTrue(filter.test(rec("WARN", "s", 1.0)));
        assertFalse(filter.test(rec("INFO", "s", 1.0)));
    }

    @Test
    void filterLevelWithNoArgumentsAcceptsAll() {
        assertTrue(Filters.level().test(rec("INFO", "s", 1.0)));
    }

    @Test
    void filterSourceMatchesOnlyListedSources() {
        LogRecordFilter filter = Filters.source("gateway");
        assertTrue(filter.test(rec("INFO", "gateway", 1.0)));
        assertFalse(filter.test(rec("INFO", "auth-service", 1.0)));
    }

    @Test
    void filterValueRangeIsInclusive() {
        LogRecordFilter filter = Filters.valueRange(0.0, 10.0);
        assertTrue(filter.test(rec("INFO", "s", 0.0)));
        assertTrue(filter.test(rec("INFO", "s", 10.0)));
        assertFalse(filter.test(rec("INFO", "s", -1.0)));
        assertFalse(filter.test(rec("INFO", "s", 11.0)));
    }

    @Test
    void filterValueRangeRejectsNaNAndInvertedBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> Filters.valueRange(Double.NaN, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> Filters.valueRange(2.0, 1.0));
    }

    @Test
    void filterAndCombinesPredicates() {
        LogRecordFilter filter =
                Filters.level("ERROR").and(Filters.valueRange(0.0, 5.0));
        assertTrue(filter.test(rec("ERROR", "s", 3.0)));
        assertFalse(filter.test(rec("ERROR", "s", 9.0)));
        assertFalse(filter.test(rec("INFO", "s", 3.0)));
    }

    @Test
    void transformerScalesValue() {
        LogRecord transformed = Transformers.scaleValue(0.5).apply(rec("INFO", "s", 10.0));
        assertEquals(5.0, transformed.value());
    }

    @Test
    void transformerScaleRejectsNonFiniteFactor() {
        assertThrows(IllegalArgumentException.class,
                () -> Transformers.scaleValue(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> Transformers.scaleValue(Double.POSITIVE_INFINITY));
    }

    @Test
    void transformerUppercasesLevel() {
        LogRecord transformed =
                Transformers.uppercaseLevel().apply(rec("error", "s", 1.0));
        assertEquals("ERROR", transformed.level());
    }

    @Test
    void aggregatorEmptyInputYieldsEmptyResult() {
        assertEquals(AggregationResult.EMPTY, new Aggregator().aggregate(List.of()));
    }

    @Test
    void aggregatorFoldsAllRecords() {
        AggregationResult result = new Aggregator().aggregate(
                List.of(rec("INFO", "s", 1.0), rec("INFO", "s", 2.0), rec("INFO", "s", 3.0)));
        assertEquals(3, result.count());
        assertEquals(6.0, result.sum());
        assertEquals(1.0, result.min());
        assertEquals(3.0, result.max());
    }

    @Test
    void processorFiltersTransformsAndRenders() {
        ChunkProcessor processor = new ChunkProcessor(
                Filters.level("ERROR"),
                Transformers.scaleValue(2.0),
                new Aggregator());

        ProcessedChunk result = processor.process(3, List.of(
                rec("ERROR", "s", 2.0),
                rec("INFO", "s", 99.0)));

        assertEquals(3, result.sequence());
        assertEquals(1, result.partial().count());
        assertEquals(4.0, result.partial().sum());
        assertEquals(1, result.outputLines().size());
        assertTrue(result.outputLines().get(0).contains(",ERROR,"));
    }

    @Test
    void processorEscapesEmbeddedCommaInCsvOutput() {
        ChunkProcessor processor = new ChunkProcessor(
                LogRecordFilter.all(),
                LogRecordTransformer.identity(),
                new Aggregator());

        ProcessedChunk result = processor.process(0, List.of(
                new LogRecord("ts", "INFO", "s", "hello, world", 1.0)));

        assertEquals("ts,INFO,s,\"hello, world\",1.0", result.outputLines().get(0));
    }

    @Test
    void processorProducesEmptyOutputWhenEverythingIsFiltered() {
        ChunkProcessor processor = new ChunkProcessor(
                Filters.level("NOPE"),
                LogRecordTransformer.identity(),
                new Aggregator());

        ProcessedChunk result = processor.process(0, List.of(rec("INFO", "s", 1.0)));

        assertEquals(0, result.partial().count());
        assertEquals(0, result.outputLines().size());
    }

    @Test
    void processorNeutralizesSpreadsheetFormulaCells() {
        ChunkProcessor processor = new ChunkProcessor(
                LogRecordFilter.all(), LogRecordTransformer.identity(), new Aggregator());

        ProcessedChunk result = processor.process(0, List.of(
                new LogRecord("ts", "INFO", "=cmd|calc", "@msg", 1.0)));

        String line = result.outputLines().get(0);
        assertEquals(true, line.contains("'=cmd|calc"),
                "cells starting with '=' must be neutralized");
        assertEquals(true, line.contains("'@msg"),
                "cells starting with '@' must be neutralized");
    }
}
