package com.jvmsim.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of processing one {@link Chunk}, produced by a worker and consumed by
 * the single asynchronous writer.
 *
 * @param sequence    global ordering key; the writer emits chunks in this order
 * @param partial     the chunk-local aggregate (merged by the orchestrator)
 * @param outputLines rendered output lines ready to be written to the target file
 */
public record ProcessedChunk(long sequence, AggregationResult partial, List<String> outputLines) {

    public ProcessedChunk {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be non-negative");
        }
        Objects.requireNonNull(partial, "partial must not be null");
        Objects.requireNonNull(outputLines, "outputLines must not be null");
        outputLines = List.copyOf(outputLines);
    }
}
