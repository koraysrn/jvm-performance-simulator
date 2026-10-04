package com.jvmsim.model;

import java.util.List;
import java.util.Objects;

/**
 * A bounded batch of raw input lines handed from the reader to a worker.
 *
 * <p>Chunking is the primary memory-control mechanism: only {@code chunkSize} raw lines
 * are materialized at a time, so heap usage stays proportional to the chunk size and the
 * number of in-flight chunks, not to the size of the input file.</p>
 *
 * @param id        monotonically increasing chunk identifier (used for ordered output)
 * @param startLine 1-based line number of the first line in this chunk (diagnostics)
 * @param lines     immutable list of raw, unparsed lines contained in this chunk
 */
public record Chunk(int id, long startLine, List<String> lines) {

    public Chunk {
        if (id < 0) {
            throw new IllegalArgumentException("id must be non-negative");
        }
        if (startLine < 0) {
            throw new IllegalArgumentException("startLine must be non-negative");
        }
        Objects.requireNonNull(lines, "lines must not be null");
        // Defensive copy guarantees immutability even if the caller mutates its list later.
        lines = List.copyOf(lines);
    }

    /** Number of raw lines in this chunk. */
    public int size() {
        return lines.size();
    }
}
