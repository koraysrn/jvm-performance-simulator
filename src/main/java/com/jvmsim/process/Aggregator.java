package com.jvmsim.process;

import com.jvmsim.model.AggregationResult;
import com.jvmsim.model.LogRecord;

import java.util.List;
import java.util.Objects;

/**
 * Folds a list of records into a partial {@link AggregationResult}.
 *
 * <p>Workers aggregate locally (no shared mutable state) and the orchestrator merges the
 * partials afterwards. {@code long} count overflow is detected via {@link Math#addExact}.</p>
 */
public final class Aggregator {

    /** Folds the given records into a single partial aggregate. */
    public AggregationResult aggregate(List<LogRecord> records) {
        Objects.requireNonNull(records, "records must not be null");
        AggregationResult accumulator = AggregationResult.EMPTY;
        for (LogRecord record : records) {
            accumulator = accumulator.add(record.value());
        }
        return accumulator;
    }

    /** Merges two partial aggregates (commutative and associative). */
    public AggregationResult merge(AggregationResult left, AggregationResult right) {
        Objects.requireNonNull(left, "left must not be null");
        Objects.requireNonNull(right, "right must not be null");
        return left.merge(right);
    }
}
