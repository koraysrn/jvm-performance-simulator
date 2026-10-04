package com.jvmsim.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordTest {

    private static LogRecord record(double value) {
        return new LogRecord("ts", "INFO", "source", "message", value);
    }

    @Test
    void logRecordRejectsNullFields() {
        assertThrows(NullPointerException.class,
                () -> new LogRecord(null, "INFO", "s", "m", 1.0));
        assertThrows(NullPointerException.class,
                () -> new LogRecord("t", null, "s", "m", 1.0));
        assertThrows(NullPointerException.class,
                () -> new LogRecord("t", "INFO", null, "m", 1.0));
        assertThrows(NullPointerException.class,
                () -> new LogRecord("t", "INFO", "s", null, 1.0));
    }

    @Test
    void logRecordRejectsNaN() {
        assertThrows(IllegalArgumentException.class,
                () -> new LogRecord("t", "INFO", "s", "m", Double.NaN));
    }

    @Test
    void logRecordEqualityAndAccessors() {
        LogRecord left = record(42.5);
        LogRecord right = record(42.5);
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertEquals("INFO", left.level());
        assertEquals(42.5, left.value());
        assertNotEquals(left, record(43.0));
    }

    @Test
    void chunkCopiesItsLineListDefensively() {
        List<String> lines = new ArrayList<>(List.of("a", "b"));
        Chunk chunk = new Chunk(0, 1, lines);
        lines.add("c");
        assertEquals(2, chunk.size());
    }

    @Test
    void chunkRejectsNegativeIdAndStartLine() {
        assertThrows(IllegalArgumentException.class,
                () -> new Chunk(-1, 1, List.of("a")));
        assertThrows(IllegalArgumentException.class,
                () -> new Chunk(0, -1, List.of("a")));
    }

    @Test
    void aggregationEmptyIsNeutralElement() {
        AggregationResult empty = AggregationResult.EMPTY;
        assertEquals(0, empty.count());
        assertEquals(Double.POSITIVE_INFINITY, empty.min());
        assertEquals(Double.NEGATIVE_INFINITY, empty.max());
    }

    @Test
    void aggregationAddFoldsValues() {
        AggregationResult result = AggregationResult.EMPTY
                .add(3.0)
                .add(1.0)
                .add(2.0);
        assertEquals(3, result.count());
        assertEquals(6.0, result.sum());
        assertEquals(1.0, result.min());
        assertEquals(3.0, result.max());
        assertEquals(2.0, result.average());
    }

    @Test
    void aggregationMergeIsCommutativeAndAssociative() {
        AggregationResult a = new AggregationResult(2, 10.0, 1.0, 9.0);
        AggregationResult b = new AggregationResult(3, 20.0, 2.0, 8.0);

        assertEquals(a.merge(b), b.merge(a));
        assertEquals(
                a.merge(b).merge(new AggregationResult(1, 5.0, 5.0, 5.0)),
                a.merge(b.merge(new AggregationResult(1, 5.0, 5.0, 5.0))));
    }

    @Test
    void aggregationAverageOfEmptyIsNaN() {
        assertEquals(Double.NaN, AggregationResult.EMPTY.average());
    }

    @Test
    void aggregationDetectsCountOverflow() {
        AggregationResult huge = new AggregationResult(Long.MAX_VALUE, 0.0, 0.0, 0.0);
        assertThrows(ArithmeticException.class, () -> huge.add(1.0));
    }

    @Test
    void processedChunkCopiesItsOutputDefensively() {
        List<String> output = new ArrayList<>(List.of("a"));
        ProcessedChunk chunk = new ProcessedChunk(0, AggregationResult.EMPTY, output);
        output.add("b");
        assertEquals(1, chunk.outputLines().size());
        assertThrows(IllegalArgumentException.class,
                () -> new ProcessedChunk(-1, AggregationResult.EMPTY, List.of()));
    }
}
