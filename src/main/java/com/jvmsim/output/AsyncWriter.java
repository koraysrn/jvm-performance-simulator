package com.jvmsim.output;

import com.jvmsim.model.ProcessedChunk;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.zip.GZIPOutputStream;

/**
 * Single, ordered, asynchronous writer.
 *
 * <p>Exactly one instance is used per run and its {@link #runLoop()} is executed as a
 * structured-concurrency subtask on a virtual thread. Workers never write directly —
 * they {@link #submit(ProcessedChunk)} results into a bounded queue, which provides
 * backpressure when the writer is slower than the workers.</p>
 *
 * <p>Chunks may arrive out of order because worker speed varies; the writer buffers them
 * in a {@link TreeMap} keyed by sequence number and emits strictly in order, so the
 * output file is always a deterministic, ordered snapshot.</p>
 */
public final class AsyncWriter implements AutoCloseable {

    /** Sentinel marking the end of the chunk stream. */
    private static final Optional<ProcessedChunk> END = Optional.empty();

    private final BlockingQueue<Optional<ProcessedChunk>> queue;
    private final BufferedWriter writer;
    private final NavigableMap<Long, ProcessedChunk> pending = new TreeMap<>();
    private long nextSequence;

    public AsyncWriter(Path outputPath, int queueCapacity, boolean gzip) throws IOException {
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        OutputStream stream = Files.newOutputStream(outputPath);
        if (gzip) {
            stream = new GZIPOutputStream(stream);
        }
        this.writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
    }

    /**
     * Hands a processed chunk to the writer. Blocks when the queue is full, which is the
     * backpressure signal propagated to workers (and transitively to the reader).
     */
    public void submit(ProcessedChunk chunk) throws InterruptedException {
        queue.put(Optional.of(chunk));
    }

    /**
     * Signals that no further chunks will be submitted. Called exactly once by the last
     * worker to finish.
     */
    public void signalEndOfInput() throws InterruptedException {
        queue.put(END);
    }

    /**
     * Writer loop. Runs as a virtual-thread subtask inside the structured scope. Any I/O
     * failure is rethrown as an unchecked exception so the enclosing
     * {@code ShutdownOnFailure} scope cancels every sibling task.
     */
    public void runLoop() {
        try {
            while (true) {
                Optional<ProcessedChunk> item = queue.take();
                if (item.isEmpty()) {
                    break;
                }
                ProcessedChunk chunk = item.get();
                pending.put(chunk.sequence(), chunk);
                while (pending.containsKey(nextSequence)) {
                    writeChunk(pending.remove(nextSequence));
                    nextSequence++;
                }
            }
            writer.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("writer interrupted", e));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write output", e);
        }
    }

    private void writeChunk(ProcessedChunk chunk) throws IOException {
        for (String line : chunk.outputLines()) {
            writer.write(line);
            writer.newLine();
        }
    }

    /** Closes the underlying stream. Safe to call more than once. */
    @Override
    public void close() throws IOException {
        writer.close();
    }
}
