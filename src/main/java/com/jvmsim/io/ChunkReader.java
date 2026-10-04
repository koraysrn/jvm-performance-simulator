package com.jvmsim.io;

import com.jvmsim.model.Chunk;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads the input file sequentially and groups raw lines into fixed-size {@link Chunk}s.
 *
 * <p>This is the memory-safety cornerstone of the pipeline: only {@code chunkSize} raw
 * lines are materialized at a time, so heap usage never scales with the full file size.
 * The reader is intentionally single-threaded — disk I/O is sequential and the real
 * parallelism happens later in the worker stage.</p>
 */
public final class ChunkReader implements AutoCloseable {

    private final BufferedReader reader;
    private final int chunkSize;
    private int nextId;
    private long linesRead;

    public ChunkReader(Path path, int chunkSize) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        this.reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        this.chunkSize = chunkSize;
    }

    /**
     * Reads the next chunk, or returns {@code null} when the file is exhausted.
     *
     * @return the next bounded chunk, or {@code null} at end of stream
     * @throws IOException when an I/O error occurs while reading
     */
    public Chunk readNext() throws IOException {
        long startLine = linesRead + 1;
        List<String> lines = new ArrayList<>(chunkSize);
        String line;
        while (lines.size() < chunkSize && (line = reader.readLine()) != null) {
            linesRead++;
            lines.add(line);
        }
        return lines.isEmpty() ? null : new Chunk(nextId++, startLine, lines);
    }

    /** Number of raw lines handed out so far (excluding lines never read). */
    public long linesRead() {
        return linesRead;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
