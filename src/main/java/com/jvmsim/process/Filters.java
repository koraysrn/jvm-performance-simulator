package com.jvmsim.process;

import com.jvmsim.model.LogRecord;

import java.util.Set;

/**
 * Static factories for common {@link LogRecordFilter} predicates.
 */
public final class Filters {

    private Filters() {
        // Utility class: no instantiation.
    }

    /** Accepts only records whose level is one of the given values. */
    public static LogRecordFilter level(String... levels) {
        if (levels == null || levels.length == 0) {
            return LogRecordFilter.all();
        }
        Set<String> allowed = Set.of(levels);
        return record -> allowed.contains(record.level());
    }

    /** Accepts only records whose source is one of the given values. */
    public static LogRecordFilter source(String... sources) {
        if (sources == null || sources.length == 0) {
            return LogRecordFilter.all();
        }
        Set<String> allowed = Set.of(sources);
        return record -> allowed.contains(record.source());
    }

    /** Accepts records whose numeric value lies within {@code [min, max]} (inclusive). */
    public static LogRecordFilter valueRange(double min, double max) {
        if (Double.isNaN(min) || Double.isNaN(max)) {
            throw new IllegalArgumentException("range bounds must not be NaN");
        }
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max");
        }
        return record -> record.value() >= min && record.value() <= max;
    }
}
