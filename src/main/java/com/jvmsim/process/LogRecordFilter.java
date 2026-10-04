package com.jvmsim.process;

import com.jvmsim.model.LogRecord;

/**
 * Functional predicate deciding whether a {@link LogRecord} should be kept.
 *
 * <p>Filters are stateless and immutable, which makes them safe to share across
 * worker virtual threads.</p>
 */
@FunctionalInterface
public interface LogRecordFilter {

    /** Returns {@code true} when the record passes the filter. */
    boolean test(LogRecord record);

    /** Combines two filters with a logical AND. */
    default LogRecordFilter and(LogRecordFilter other) {
        return record -> test(record) && other.test(record);
    }

    /** Combines two filters with a logical OR. */
    default LogRecordFilter or(LogRecordFilter other) {
        return record -> test(record) || other.test(record);
    }

    /** Filter that accepts every record. */
    static LogRecordFilter all() {
        return record -> true;
    }
}
