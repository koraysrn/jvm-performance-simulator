package com.jvmsim.process;

import com.jvmsim.model.LogRecord;

/**
 * Functional mapping that converts one {@link LogRecord} into another.
 *
 * <p>Because {@link LogRecord} is immutable, transformers always return a new instance;
 * the original record is never mutated.</p>
 */
@FunctionalInterface
public interface LogRecordTransformer {

    /** Applies the transformation and returns a new record. */
    LogRecord apply(LogRecord record);

    /** Transformer that returns the record unchanged. */
    static LogRecordTransformer identity() {
        return record -> record;
    }
}
