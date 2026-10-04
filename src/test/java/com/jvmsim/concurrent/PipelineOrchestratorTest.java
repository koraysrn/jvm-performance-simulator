package com.jvmsim.concurrent;

import com.jvmsim.config.PipelineConfig;
import java.io.IOException;
import com.jvmsim.model.LogRecord;
import com.jvmsim.parse.CsvParser;
import com.jvmsim.parse.MalformedLineException;
import com.jvmsim.parse.MalformedLinePolicy;
import com.jvmsim.process.Aggregator;
import com.jvmsim.process.ChunkProcessor;
import com.jvmsim.process.Filters;
import com.jvmsim.process.LogRecordFilter;
import com.jvmsim.process.LogRecordTransformer;
import com.jvmsim.testutil.DataGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineOrchestratorTest {

    @TempDir
    Path tempDir;

    private PipelineConfig config(Path input, Path output,
                                  MalformedLinePolicy policy, long deadlineSeconds) {
        return new PipelineConfig(input, output, 100, 4, 16, false,
                policy, null, null, null, null, null, deadlineSeconds);
    }

    private PipelineOrchestrator orchestrator(PipelineConfig config,
                                              ChunkProcessor processor) {
        return new PipelineOrchestrator(config, new CsvParser(config.malformedPolicy()), processor);
    }

    private ChunkProcessor identityProcessor() {
        return new ChunkProcessor(
                LogRecordFilter.all(), LogRecordTransformer.identity(), new Aggregator());
    }

    @Test
    void endToEndProcessesEveryLineInOrder() throws Exception {
        Path input = tempDir.resolve("in.csv");
        Path output = tempDir.resolve("out.csv");
        DataGenerator.generate(input, 1000, 42L);

        PipelineResult result = orchestrator(
                config(input, output, MalformedLinePolicy.SKIP, 0),
                identityProcessor()).run();

        assertEquals(1000, result.linesRead());
        assertEquals(1000, result.recordsProcessed());
        assertEquals(0, result.malformedLines());

        // Output must preserve input order and content (Double.toString round-trips).
        assertEquals(Files.readAllLines(input), Files.readAllLines(output));
    }

    @Test
    void filterReducesOutputAndAggregate() throws Exception {
        Path input = tempDir.resolve("in.csv");
        Path output = tempDir.resolve("out.csv");
        DataGenerator.generate(input, 1000, 42L);

        ChunkProcessor errorOnly = new ChunkProcessor(
                Filters.level("ERROR"), LogRecordTransformer.identity(), new Aggregator());

        PipelineResult result = orchestrator(
                config(input, output, MalformedLinePolicy.SKIP, 0), errorOnly).run();

        // LEVELS[index % 5] == "ERROR" for index % 5 == 2 -> 200 of 1000 lines.
        assertEquals(200, result.recordsProcessed());
        assertEquals(200, result.aggregate().count());
        assertEquals(200, Files.readAllLines(output).size());
    }

    @Test
    void failFastAbortsWholePipelineOnMalformedLine() throws Exception {
        Path input = tempDir.resolve("bad.csv");
        Path output = tempDir.resolve("out.csv");
        Files.writeString(input, "ts,INFO,s,m,1.0\nBROKEN,LINE\n");

        PipelineOrchestrator orchestrator = orchestrator(
                config(input, output, MalformedLinePolicy.FAIL_FAST, 0),
                identityProcessor());

        ExecutionException ex =
                assertThrows(ExecutionException.class, orchestrator::run);
        assertInstanceOf(MalformedLineException.class, ex.getCause());
    }

    @Test
    void skipPolicyKeepsRunningPastMalformedLines() throws Exception {
        Path input = tempDir.resolve("bad.csv");
        Path output = tempDir.resolve("out.csv");
        Files.writeString(input, "ts,INFO,s,m,1.0\nBROKEN,LINE\nts2,WARN,s,m,2.0\n");

        PipelineResult result = orchestrator(
                config(input, output, MalformedLinePolicy.SKIP, 0),
                identityProcessor()).run();

        assertEquals(3, result.linesRead());
        assertEquals(2, result.recordsProcessed());
        assertEquals(1, result.malformedLines());
        assertEquals(2, Files.readAllLines(output).size());
    }

    @Test
    void deadlineExceededThrowsTimeout() throws Exception {
        Path input = tempDir.resolve("in.csv");
        Path output = tempDir.resolve("out.csv");
        Files.writeString(input,
                "t1,INFO,s,m,1\nt2,INFO,s,m,2\nt3,INFO,s,m,3\nt4,INFO,s,m,4\nt5,INFO,s,m,5\n");

        LogRecordTransformer slow = record -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return record;
        };
        ChunkProcessor slowProcessor = new ChunkProcessor(
                LogRecordFilter.all(), slow, new Aggregator());

        // Single worker: 5 records * 500 ms = 2.5 s, deterministically past the 1 s deadline.
        PipelineConfig config = new PipelineConfig(
                input, output, 100, 1, 8, false,
                MalformedLinePolicy.SKIP, null, null, null, null, null, 1);
        PipelineOrchestrator orchestrator = orchestrator(config, slowProcessor);

        assertThrows(TimeoutException.class, orchestrator::run);
    }

    @Test
    void repeatedRunsProduceByteIdenticalOutput() throws Exception {
        Path input = tempDir.resolve("in.csv");
        Path first = tempDir.resolve("first.csv");
        Path second = tempDir.resolve("second.csv");
        DataGenerator.generate(input, 5000, 42L);

        PipelineConfig firstConfig =
                config(input, first, MalformedLinePolicy.SKIP, 0);
        PipelineConfig secondConfig =
                config(input, second, MalformedLinePolicy.SKIP, 0);

        orchestrator(firstConfig, identityProcessor()).run();
        orchestrator(secondConfig, identityProcessor()).run();

        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    }

    @Test
    void goldenMasterMatchesHandWrittenExpectation() throws Exception {
        Path input = tempDir.resolve("golden-in.csv");
        Path output = tempDir.resolve("golden-out.csv");
        Files.writeString(input,
                "t1,INFO,a,one,1.0\nt2,ERROR,b,two,2.0\nt3,WARN,c,three,3.0\n");

        ChunkProcessor errorOnly = new ChunkProcessor(
                Filters.level("ERROR"), LogRecordTransformer.identity(), new Aggregator());

        orchestrator(config(input, output, MalformedLinePolicy.SKIP, 0), errorOnly).run();

        assertEquals(List.of("t2,ERROR,b,two,2.0"), Files.readAllLines(output));
    }

    @Test
    void writerIoFailurePropagates() {
        Path missingDirectory = tempDir.resolve("no-such-dir");
        Path input = tempDir.resolve("in.csv");
        PipelineConfig config = new PipelineConfig(
                input, missingDirectory.resolve("out.csv"),
                100, 2, 8, false,
                MalformedLinePolicy.SKIP, null, null, null, null, null, 0);

        PipelineOrchestrator orchestrator = orchestrator(config, identityProcessor());

        // The writer is constructed before any subtask starts, so its I/O failure
        // surfaces directly as an IOException from run().
        assertThrows(IOException.class, orchestrator::run);
    }

    @Test
    void readerIoFailurePropagates() {
        Path missingInput = tempDir.resolve("missing.csv");
        Path output = tempDir.resolve("out.csv");
        PipelineConfig config = new PipelineConfig(
                missingInput, output, 100, 2, 8, false,
                MalformedLinePolicy.SKIP, null, null, null, null, null, 0);

        PipelineOrchestrator orchestrator = orchestrator(config, identityProcessor());

        // The partitioner probes the file before any subtask starts, so the I/O failure
        // surfaces directly as an IOException from run().
        assertThrows(IOException.class, orchestrator::run);
    }

    @Test
    void noPipelineThreadsRemainAfterRun() throws Exception {
        Path input = tempDir.resolve("in.csv");
        Path output = tempDir.resolve("out.csv");
        DataGenerator.generate(input, 10_000, 42L);

        orchestrator(config(input, output, MalformedLinePolicy.SKIP, 0),
                identityProcessor()).run();

        // StructuredTaskScope.join() has already completed every subtask; a short grace
        // period removes any residual bookkeeping before we count live threads.
        Thread.sleep(100);
        long leaked = Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isVirtual)
                .filter(thread -> thread.getName().startsWith("pipeline-worker"))
                .count();

        assertEquals(0, leaked, "pipeline must not leak worker virtual threads");
    }

    @Test
    void interruptedRunLeavesOnlyCompleteLines() throws Exception {
        Path input = tempDir.resolve("in.csv");
        Path output = tempDir.resolve("out.csv");
        Files.writeString(input,
                "t1,INFO,s,m,1\nt2,INFO,s,m,2\nt3,INFO,s,m,3\nt4,INFO,s,m,4\nt5,INFO,s,m,5\n");

        LogRecordTransformer slow = record -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return record;
        };
        ChunkProcessor slowProcessor = new ChunkProcessor(
                LogRecordFilter.all(), slow, new Aggregator());

        // Single worker: 5 records * 500 ms = 2.5 s, deterministically past the 1 s deadline.
        PipelineConfig config = new PipelineConfig(
                input, output, 100, 1, 8, false,
                MalformedLinePolicy.SKIP, null, null, null, null, null, 1);
        PipelineOrchestrator orchestrator = orchestrator(config, slowProcessor);

        assertThrows(TimeoutException.class, orchestrator::run);

        // Whatever reached the output must be a complete 5-column CSV record.
        if (Files.exists(output)) {
            for (String line : Files.readAllLines(output)) {
                if (line.isBlank()) {
                    continue;
                }
                assertEquals(4, line.split(",", -1).length - 1,
                        "expected 5 CSV columns in line: " + line);
            }
        }
    }
}
