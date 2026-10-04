package com.jvmsim.config;

import com.jvmsim.parse.MalformedLinePolicy;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable configuration snapshot for a single pipeline run.
 *
 * <p>Plain data only: this record carries values and validates them, but deliberately
 * has no dependency on the processing layer. Filter/transformer construction happens in
 * the CLI bootstrap layer.</p>
 *
 * @param inputPath       source file to read
 * @param outputPath      destination file to write
 * @param chunkSize       number of raw lines per chunk
 * @param workerCount     number of worker virtual threads
 * @param queueCapacity   bounded capacity of the reader/worker and worker/writer queues
 * @param gzip            whether the output should be GZIP compressed
 * @param malformedPolicy how to react to unparsable lines
 * @param levelFilter     comma-separated accepted levels, or {@code null} for all
 * @param sourceFilter    comma-separated accepted sources, or {@code null} for all
 * @param minValue        inclusive lower bound, or {@code null} for unbounded
 * @param maxValue        inclusive upper bound, or {@code null} for unbounded
 * @param valueScale      multiplication factor applied to values, or {@code null} for identity
 * @param deadlineSeconds deadline for the whole run, or {@code 0} for no deadline
 */
public record PipelineConfig(
        Path inputPath,
        Path outputPath,
        int chunkSize,
        int workerCount,
        int queueCapacity,
        boolean gzip,
        MalformedLinePolicy malformedPolicy,
        String levelFilter,
        String sourceFilter,
        Double minValue,
        Double maxValue,
        Double valueScale,
        long deadlineSeconds) {

    public PipelineConfig {
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(malformedPolicy, "malformedPolicy must not be null");
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        if (deadlineSeconds < 0) {
            throw new IllegalArgumentException("deadlineSeconds must be non-negative");
        }
        if (minValue != null && maxValue != null && minValue > maxValue) {
            throw new IllegalArgumentException("minValue must be <= maxValue");
        }
        if (valueScale != null && !Double.isFinite(valueScale)) {
            throw new IllegalArgumentException("valueScale must be finite");
        }
    }
}
