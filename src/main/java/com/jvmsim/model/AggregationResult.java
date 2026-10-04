package com.jvmsim.model;

import java.util.Objects;

/**
 * Immutable partial aggregation produced by a single worker for a single {@link Chunk}.
 *
 * <p>Workers never mutate a shared aggregate. Each worker computes a local result and
 * the orchestrator merges them afterwards. This map-reduce style split keeps the
 * concurrent path lock-free.</p>
 *
 * @param count number of records contributing to this aggregate
 * @param sum   running sum of {@code value} across contributing records
 * @param min   minimum observed value ({@link Double#POSITIVE_INFINITY} when empty)
 * @param max   maximum observed value ({@link Double#NEGATIVE_INFINITY} when empty)
 */
public record AggregationResult(long count, double sum, double min, double max) {

    /** Neutral element used before the first record is folded in. */
    public static final AggregationResult EMPTY =
            new AggregationResult(0L, 0.0d, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);

    public AggregationResult {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        if (Double.isNaN(sum) || Double.isNaN(min) || Double.isNaN(max)) {
            throw new IllegalArgumentException("aggregate fields must not be NaN");
        }
    }

    /**
     * Folds a single value into this aggregate without allocating a new object.
     * Used by workers inside the hot loop to minimize garbage pressure.
     *
     * @param value the value to fold in
     * @return the updated aggregate
     */
    public AggregationResult add(double value) {
        return new AggregationResult(
                Math.addExact(count, 1L),
                sum + value,
                Math.min(min, value),
                Math.max(max, value));
    }

    /**
     * Combines two partial aggregates. The operation is commutative and associative,
     * which guarantees that the final result is independent of chunk partitioning.
     *
     * @param other the other partial aggregate
     * @return the merged aggregate
     */
    public AggregationResult merge(AggregationResult other) {
        Objects.requireNonNull(other, "other must not be null");
        return new AggregationResult(
                Math.addExact(count, other.count),
                sum + other.sum,
                Math.min(min, other.min),
                Math.max(max, other.max));
    }

    /** Average of the folded values, or {@code NaN} when no record was observed. */
    public double average() {
        return count == 0 ? Double.NaN : sum / count;
    }
}
