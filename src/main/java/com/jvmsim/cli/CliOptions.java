package com.jvmsim.cli;

import com.jvmsim.concurrent.PipelineOrchestrator;
import com.jvmsim.concurrent.PipelineResult;
import com.jvmsim.config.PipelineConfig;
import com.jvmsim.metrics.PipelineMetrics;
import com.jvmsim.parse.CsvParser;
import com.jvmsim.parse.MalformedLinePolicy;
import com.jvmsim.process.Aggregator;
import com.jvmsim.process.ChunkProcessor;
import com.jvmsim.process.Filters;
import com.jvmsim.process.LogRecordFilter;
import com.jvmsim.process.LogRecordTransformer;
import com.jvmsim.process.Transformers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Picocli command definition and run bootstrap.
 *
 * <p>Translates raw CLI arguments into an immutable {@link PipelineConfig}, constructs
 * the processing chain and starts the orchestrated pipeline.</p>
 */
@Command(
        name = "jvm-sim",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = "Processes a large CSV/log file in chunks using Java 21 virtual threads.")
public final class CliOptions implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(CliOptions.class);

    @Option(names = {"-i", "--input"}, required = true, description = "Input CSV file path")
    private Path input;

    @Option(names = {"-o", "--output"}, required = true, description = "Output CSV file path")
    private Path output;

    @Option(names = "--chunk-size", defaultValue = "2000", description = "Lines per chunk")
    private int chunkSize;

    @Option(names = "--workers", defaultValue = "8", description = "Number of worker virtual threads")
    private int workers;

    @Option(names = "--queue-capacity", defaultValue = "32", description = "Bounded queue capacity (backpressure)")
    private int queueCapacity;

    @Option(names = "--gzip", defaultValue = "false", description = "GZIP-compress the output")
    private boolean gzip;

    @Option(names = "--fail-fast", defaultValue = "false", description = "Abort on the first malformed line")
    private boolean failFast;

    @Option(names = "--level", description = "Comma-separated accepted levels (e.g. ERROR,WARN)")
    private String level;

    @Option(names = "--source", description = "Comma-separated accepted sources")
    private String source;

    @Option(names = "--min-value", description = "Inclusive lower bound for the numeric value")
    private Double minValue;

    @Option(names = "--max-value", description = "Inclusive upper bound for the numeric value")
    private Double maxValue;

    @Option(names = "--value-scale", description = "Multiplication factor applied to values")
    private Double valueScale;

    @Option(names = "--deadline-seconds", defaultValue = "0", description = "Whole-run deadline (0 = no deadline)")
    private long deadlineSeconds;

    @Override
    public Integer call() throws Exception {
        PipelineConfig config = toConfig();
        CsvParser parser = new CsvParser(config.malformedPolicy());
        ChunkProcessor processor = buildProcessor(config);
        PipelineOrchestrator orchestrator = new PipelineOrchestrator(config, parser, processor);

        PipelineResult result = orchestrator.run();

        new PipelineMetrics(new SimpleMeterRegistry()).record(result);
        report(result);
        return 0;
    }

    private PipelineConfig toConfig() {
        return new PipelineConfig(
                input,
                output,
                chunkSize,
                workers,
                queueCapacity,
                gzip,
                failFast ? MalformedLinePolicy.FAIL_FAST : MalformedLinePolicy.SKIP,
                level,
                source,
                minValue,
                maxValue,
                valueScale,
                deadlineSeconds);
    }

    private ChunkProcessor buildProcessor(PipelineConfig config) {
        LogRecordFilter filter = LogRecordFilter.all();

        if (config.levelFilter() != null && !config.levelFilter().isBlank()) {
            filter = filter.and(Filters.level(config.levelFilter().split(",")));
        }
        if (config.sourceFilter() != null && !config.sourceFilter().isBlank()) {
            filter = filter.and(Filters.source(config.sourceFilter().split(",")));
        }
        if (config.minValue() != null || config.maxValue() != null) {
            double min = config.minValue() == null
                    ? Double.NEGATIVE_INFINITY : config.minValue();
            double max = config.maxValue() == null
                    ? Double.POSITIVE_INFINITY : config.maxValue();
            filter = filter.and(Filters.valueRange(min, max));
        }

        LogRecordTransformer transformer = config.valueScale() == null
                ? LogRecordTransformer.identity()
                : Transformers.scaleValue(config.valueScale());

        return new ChunkProcessor(filter, transformer, new Aggregator());
    }

    private void report(PipelineResult result) {
        LOG.info("Pipeline finished: linesRead={}, recordsProcessed={}, malformed={}, duration={}",
                result.linesRead(), result.recordsProcessed(),
                result.malformedLines(), result.duration());
        LOG.info("Aggregate: count={}, sum={}, min={}, max={}, avg={}",
                result.aggregate().count(), result.aggregate().sum(),
                result.aggregate().min(), result.aggregate().max(),
                result.aggregate().average());
    }
}
