package com.jvmsim.process;

import com.jvmsim.model.LogRecord;

/**
 * Static factories for common {@link LogRecordTransformer} mappings.
 */
public final class Transformers {

    private Transformers() {
        // Utility class: no instantiation.
    }

    /** Scales the numeric value by the given factor (e.g. milliseconds to seconds). */
    public static LogRecordTransformer scaleValue(double factor) {
        if (!Double.isFinite(factor)) {
            throw new IllegalArgumentException("factor must be finite");
        }
        return record -> new LogRecord(
                record.timestamp(),
                record.level(),
                record.source(),
                record.message(),
                record.value() * factor);
    }

    /** Uppercases the severity level (INFO stays INFO, info becomes INFO). */
    public static LogRecordTransformer uppercaseLevel() {
        return record -> new LogRecord(
                record.timestamp(),
                record.level().toUpperCase(),
                record.source(),
                record.message(),
                record.value());
    }
}
