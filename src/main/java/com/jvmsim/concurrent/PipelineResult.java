package com.jvmsim.concurrent;

import com.jvmsim.model.AggregationResult;

import java.time.Duration;

/**
 * Immutable summary produced by a completed pipeline run.
 *
 * @param linesRead        raw lines read from the input file
 * @param recordsProcessed records that passed the filter (equal to {@code aggregate.count()})
 * @param malformedLines   lines that could not be decoded
 * @param aggregate        merged aggregate across all workers
 * @param duration         wall-clock time of the run
 */
public record PipelineResult(
        long linesRead,
        long recordsProcessed,
        long malformedLines,
        AggregationResult aggregate,
        Duration duration) {
}
