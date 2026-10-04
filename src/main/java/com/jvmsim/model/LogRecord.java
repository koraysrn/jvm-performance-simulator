package com.jvmsim.model;

import java.util.Objects;

/**
 * Immutable data-transfer object representing a single parsed line of the input file.
 *
 * <p>The CSV schema is fixed to five columns:
 * {@code timestamp, level, source, message, value}.</p>
 *
 * <p>As a Java {@link Record}, every instance is deeply immutable and therefore safe to
 * share across virtual threads without synchronization.</p>
 *
 * @param timestamp event timestamp (raw text, kept untouched by the parser)
 * @param level     severity level (INFO, WARN, ERROR, ...)
 * @param source    originating component or service
 * @param message   free-form message payload
 * @param value     numeric metric used for filtering and aggregation
 */
public record LogRecord(
        String timestamp,
        String level,
        String source,
        String message,
        double value) {

    /** Compact constructor: validates invariants for every construction path. */
    public LogRecord {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(level, "level must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("value must not be NaN");
        }
    }
}
