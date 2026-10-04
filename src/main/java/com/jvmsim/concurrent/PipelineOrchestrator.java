package com.jvmsim.concurrent;

import com.jvmsim.config.PipelineConfig;
import com.jvmsim.io.ByteRange;
import com.jvmsim.io.ByteRangePartitioner;
import com.jvmsim.io.RangeChunkReader;
import com.jvmsim.model.AggregationResult;
import com.jvmsim.model.Chunk;
import com.jvmsim.model.LogRecord;
import com.jvmsim.model.ProcessedChunk;
import com.jvmsim.output.AsyncWriter;
import com.jvmsim.parse.CsvParser;
import com.jvmsim.process.ChunkProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates the end-to-end pipeline using structured concurrency.
 *
 * <p>Every concurrent task — readers, merger, workers and writer — is a subtask of a
 * single {@link StructuredTaskScope.ShutdownOnFailure} running on virtual threads. This
 * guarantees that no orphan threads leak out of the scope and that a failure in any
 * stage cancels all sibling stages.</p>
 *
 * <p>The input file is split into byte ranges aligned to line boundaries
 * ({@link ByteRangePartitioner}). One reader scans each range in parallel and feeds a
 * bounded per-reader queue. A single merger consumes those queues in partition order,
 * assigns global sequence ids and hands chunks to the worker pool, preserving output
 * order. Every queue in the graph is bounded, so memory stays under a fixed budget and
 * backpressure propagates end-to-end.</p>
 */
public final class PipelineOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineOrchestrator.class);

    /** Run identifier propagated to every subtask via a scoped value (Java 21 preview). */
    private static final ScopedValue<String> RUN_ID = ScopedValue.newInstance();

    private final PipelineConfig config;
    private final CsvParser parser;
    private final ChunkProcessor processor;

    public PipelineOrchestrator(PipelineConfig config,
                                CsvParser parser,
                                ChunkProcessor processor) {
        this.config = config;
        this.parser = parser;
        this.processor = processor;
    }

    /** Runs the pipeline and returns a summary. */
    public PipelineResult run() throws Exception {
        String runId = UUID.randomUUID().toString();
        return ScopedValue.where(RUN_ID, runId).call(this::execute);
    }

    private PipelineResult execute() throws Exception {
        long startNanos = System.nanoTime();

        AsyncWriter writer = new AsyncWriter(
                config.outputPath(), config.queueCapacity(), config.gzip());
        BlockingQueue<Optional<Chunk>> workQueue =
                new ArrayBlockingQueue<>(config.queueCapacity());
        AtomicReference<AggregationResult> total =
                new AtomicReference<>(AggregationResult.EMPTY);
        AtomicInteger workersDone = new AtomicInteger();
        AtomicLong linesRead = new AtomicLong();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure(
                "pipeline-" + RUN_ID.get(),
                Thread.ofVirtual().name("pipeline-worker", 0).factory())) {

            // Writer subtask: consumes and orders all processed chunks.
            scope.fork(() -> {
                writer.runLoop();
                return null;
            });

            // Byte-offset partitioning with one parallel reader per range.
            int readerCount = Math.max(1, config.workerCount());
            List<ByteRange> ranges =
                    ByteRangePartitioner.partition(config.inputPath(), readerCount);
            List<BlockingQueue<Optional<Chunk>>> readerQueues = new ArrayList<>(ranges.size());
            for (int i = 0; i < ranges.size(); i++) {
                readerQueues.add(new ArrayBlockingQueue<>(config.queueCapacity()));
            }

            for (int i = 0; i < ranges.size(); i++) {
                final int r = i;
                scope.fork(() -> {
                    try (RangeChunkReader reader = new RangeChunkReader(
                            config.inputPath(), ranges.get(r), config.chunkSize())) {
                        Chunk chunk;
                        while ((chunk = reader.readNext()) != null) {
                            readerQueues.get(r).put(Optional.of(chunk));
                        }
                        linesRead.addAndGet(reader.linesRead());
                    }
                    readerQueues.get(r).put(Optional.empty());
                    return null;
                });
            }

            // Merger subtask: emits chunks in partition order with global sequence ids.
            scope.fork(() -> {
                long sequence = 0;
                for (BlockingQueue<Optional<Chunk>> queue : readerQueues) {
                    while (true) {
                        Optional<Chunk> item = queue.take();
                        if (item.isEmpty()) {
                            break;
                        }
                        Chunk original = item.get();
                        Chunk tagged = new Chunk(
                                (int) sequence++, original.startLine(), original.lines());
                        workQueue.put(Optional.of(tagged));
                    }
                }
                for (int i = 0; i < config.workerCount(); i++) {
                    workQueue.put(Optional.empty());
                }
                return null;
            });

            // Worker subtasks: parse -> process -> merge partial -> hand off to writer.
            for (int i = 0; i < config.workerCount(); i++) {
                scope.fork(() -> {
                    runWorker(workQueue, writer, total, workersDone);
                    return null;
                });
            }

            if (config.deadlineSeconds() > 0) {
                scope.joinUntil(Instant.now().plusSeconds(config.deadlineSeconds()));
            } else {
                scope.join();
            }
            scope.throwIfFailed();
        } finally {
            writer.close();
        }

        return new PipelineResult(
                linesRead.get(),
                total.get().count(),
                parser.malformedCount(),
                total.get(),
                Duration.ofNanos(System.nanoTime() - startNanos));
    }

    private void runWorker(BlockingQueue<Optional<Chunk>> workQueue,
                           AsyncWriter writer,
                           AtomicReference<AggregationResult> total,
                           AtomicInteger workersDone) throws InterruptedException {
        while (true) {
            Optional<Chunk> maybe = workQueue.take();
            if (maybe.isEmpty()) {
                break;
            }
            Chunk chunk = maybe.get();
            LOG.debug("run {} processing chunk {}", RUN_ID.get(), chunk.id());

            List<LogRecord> records = parseChunk(chunk);
            ProcessedChunk result = processor.process(chunk.id(), records);

            total.accumulateAndGet(result.partial(), AggregationResult::merge);
            writer.submit(result);
        }

        // The last worker to finish closes the writer stream.
        if (workersDone.incrementAndGet() == config.workerCount()) {
            writer.signalEndOfInput();
        }
    }

    private List<LogRecord> parseChunk(Chunk chunk) {
        return parser.parseChunk(chunk.lines(), chunk.startLine());
    }
}
