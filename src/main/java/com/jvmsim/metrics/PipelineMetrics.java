package com.jvmsim.metrics;

import com.jvmsim.concurrent.PipelineResult;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;

/**
 * Records summary metrics for a finished pipeline run into a Micrometer registry.
 *
 * <p>Counters and timers are cumulative, so repeated runs on the same registry produce
 * aggregate statistics; a fresh registry is used per CLI invocation.</p>
 */
public final class PipelineMetrics {

    private final MeterRegistry registry;

    public PipelineMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /** Publishes the outcome of a single run. */
    public void record(PipelineResult result) {
        Objects.requireNonNull(result, "result must not be null");
        registry.counter("pipeline.runs").increment();
        registry.counter("pipeline.lines.read").increment(result.linesRead());
        registry.counter("pipeline.records.processed").increment(result.recordsProcessed());
        registry.counter("pipeline.lines.malformed").increment(result.malformedLines());
        registry.timer("pipeline.duration").record(result.duration());
    }
}
