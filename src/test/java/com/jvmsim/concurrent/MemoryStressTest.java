package com.jvmsim.concurrent;

import com.jvmsim.config.PipelineConfig;
import com.jvmsim.parse.CsvParser;
import com.jvmsim.parse.MalformedLinePolicy;
import com.jvmsim.process.Aggregator;
import com.jvmsim.process.ChunkProcessor;
import com.jvmsim.process.LogRecordFilter;
import com.jvmsim.process.LogRecordTransformer;
import com.jvmsim.testutil.DataGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Heavy stress test excluded from the default build via the {@code stress} tag.
 *
 * <p>The whole test JVM runs with {@code -Xmx256m} (configured in surefire), proving
 * that the pipeline processes a 10M-line file without {@code OutOfMemoryError}. The line
 * count can be lowered for quick local checks with
 * {@code -Dstress.lines=100000}.</p>
 */
@Tag("stress")
class MemoryStressTest {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryStressTest.class);

    @TempDir
    Path tempDir;

    @Test
    void processesTenMillionLinesWithinBoundedHeap() throws Exception {
        int lines = Integer.getInteger("stress.lines", 10_000_000);

        Path input = tempDir.resolve("stress-in.csv");
        Path output = tempDir.resolve("stress-out.csv");
        DataGenerator.generate(input, lines, 7L);

        // Chunk size and queue capacity are chosen so that the maximum number of
        // in-flight raw lines stays well below the fixed 256 MB heap budget:
        //   live lines ~= chunkSize * (queueCapacity * 2 + workerCount).
        PipelineConfig config = new PipelineConfig(
                input, output, 2_000, 8, 32, false,
                MalformedLinePolicy.SKIP, null, null, null, null, null, 0);
        ChunkProcessor processor = new ChunkProcessor(
                LogRecordFilter.all(), LogRecordTransformer.identity(), new Aggregator());
        PipelineOrchestrator orchestrator =
                new PipelineOrchestrator(config, new CsvParser(MalformedLinePolicy.SKIP), processor);

        PipelineResult result = orchestrator.run();

        assertEquals(lines, result.linesRead());
        assertEquals(lines, result.recordsProcessed());
        assertEquals(0, result.malformedLines());

        long millis = Math.max(1, result.duration().toMillis());
        LOG.info("Stress run: {} lines processed in {} ms (~{} lines/s)",
                lines, millis, lines * 1000L / millis);
    }
}
