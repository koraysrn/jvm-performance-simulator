package com.jvmsim.output;

import com.jvmsim.model.AggregationResult;
import com.jvmsim.model.ProcessedChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsyncWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesChunksInSequenceOrderDespiteOutOfOrderArrival() throws Exception {
        Path output = tempDir.resolve("out.csv");

        try (AsyncWriter writer = new AsyncWriter(output, 16, false)) {
            writer.submit(new ProcessedChunk(1, AggregationResult.EMPTY, List.of("line-1")));
            writer.submit(new ProcessedChunk(0, AggregationResult.EMPTY, List.of("line-0")));
            writer.signalEndOfInput();

            Thread runner = Thread.ofVirtual().start(writer::runLoop);
            runner.join();
        }

        assertEquals(List.of("line-0", "line-1"), Files.readAllLines(output));
    }

    @Test
    void writesGzipCompressedOutput() throws Exception {
        Path output = tempDir.resolve("out.csv.gz");

        try (AsyncWriter writer = new AsyncWriter(output, 16, true)) {
            writer.submit(new ProcessedChunk(0, AggregationResult.EMPTY, List.of("a,b")));
            writer.signalEndOfInput();

            Thread runner = Thread.ofVirtual().start(writer::runLoop);
            runner.join();
        }

        try (GZIPInputStream gzip = new GZIPInputStream(Files.newInputStream(output));
             BufferedReader reader =
                     new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
            assertEquals("a,b", reader.readLine());
        }
    }

    @Test
    void rejectsNonPositiveQueueCapacity() {
        Path output = tempDir.resolve("out.csv");
        assertThrows(IllegalArgumentException.class, () -> new AsyncWriter(output, 0, false));
    }

    @Test
    void submitBlocksWhileTheQueueIsFull() throws Exception {
        Path output = tempDir.resolve("out.csv");

        try (AsyncWriter writer = new AsyncWriter(output, 1, false)) {
            // Fill the single-slot queue.
            writer.submit(new ProcessedChunk(0, AggregationResult.EMPTY, List.of("first")));

            CountDownLatch started = new CountDownLatch(1);
            AtomicBoolean blocked = new AtomicBoolean(true);
            Thread producer = Thread.ofVirtual().start(() -> {
                started.countDown();
                try {
                    writer.submit(new ProcessedChunk(1, AggregationResult.EMPTY, List.of("second")));
                    blocked.set(false);
                    writer.signalEndOfInput();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            started.await();
            Thread.sleep(200);
            assertEquals(true, blocked.get(),
                    "second submit should block while the queue is full (backpressure)");

            // Draining the queue unblocks the producer, which then signals end-of-input.
            Thread runner = Thread.ofVirtual().start(writer::runLoop);
            runner.join();
            producer.join();
        }

        assertEquals(List.of("first", "second"), Files.readAllLines(output));
    }

    @Test
    void writerFailurePropagatesAsUncheckedIOException() throws Exception {
        Path output = tempDir.resolve("out.csv");

        try (AsyncWriter writer = new AsyncWriter(output, 16, false)) {
            writer.submit(new ProcessedChunk(0, AggregationResult.EMPTY, List.of("x")));
            writer.signalEndOfInput();
            writer.close(); // close the stream before the loop, forcing a write failure

            assertThrows(UncheckedIOException.class, writer::runLoop);
        }
    }
}
