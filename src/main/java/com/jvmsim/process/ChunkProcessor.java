package com.jvmsim.process;

import com.jvmsim.model.AggregationResult;
import com.jvmsim.model.LogRecord;
import com.jvmsim.model.ProcessedChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stateless, thread-safe unit of work executed by each worker virtual thread.
 *
 * <p>For a given sequence of parsed records it applies the filter, transforms the
 * surviving records, folds them into a partial aggregate and renders the transformed
 * records to CSV lines. The result is an immutable {@link ProcessedChunk} carrying a
 * global sequence number that the writer uses to preserve output ordering.</p>
 */
public final class ChunkProcessor {

    private final LogRecordFilter filter;
    private final LogRecordTransformer transformer;
    private final Aggregator aggregator;

    public ChunkProcessor(LogRecordFilter filter,
                          LogRecordTransformer transformer,
                          Aggregator aggregator) {
        this.filter = Objects.requireNonNull(filter, "filter must not be null");
        this.transformer = Objects.requireNonNull(transformer, "transformer must not be null");
        this.aggregator = Objects.requireNonNull(aggregator, "aggregator must not be null");
    }

    /**
     * Processes an already-parsed batch of records.
     *
     * @param sequence global ordering key (usually the chunk id)
     * @param records  parsed records belonging to this batch
     * @return the immutable processed result
     */
    public ProcessedChunk process(long sequence, List<LogRecord> records) {
        Objects.requireNonNull(records, "records must not be null");

        List<String> outputLines = new ArrayList<>();
        AggregationResult accumulator = AggregationResult.EMPTY;

        for (LogRecord record : records) {
            if (!filter.test(record)) {
                continue;
            }
            LogRecord transformed = transformer.apply(record);
            outputLines.add(render(transformed));
            accumulator = accumulator.add(transformed.value());
        }

        return new ProcessedChunk(sequence, accumulator, outputLines);
    }

    /** Renders a record as an RFC-4180 compatible CSV line. */
    private static String render(LogRecord record) {
        return String.join(",",
                csvEscape(record.timestamp()),
                csvEscape(record.level()),
                csvEscape(record.source()),
                csvEscape(record.message()),
                Double.toString(record.value()));
    }

    /** Escapes a CSV field for delimiters/quotes and neutralizes spreadsheet formulas. */
    private static String csvEscape(String field) {
        String safe = field;
        // OWASP CSV/Formula Injection (CWE-1236): prefix cells that could be interpreted
        // as formulas when the output is opened in Excel/LibreOffice.
        if (!safe.isEmpty()) {
            char first = safe.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@'
                    || first == '\t' || first == '\r') {
                safe = "'" + safe;
            }
        }
        if (safe.contains(",") || safe.contains("\"")) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }
}
