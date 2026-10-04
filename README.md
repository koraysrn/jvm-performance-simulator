# JVM Performance Simulator

An end-to-end batch pipeline that reads a large (10M+ line) CSV/log file in chunks,
processes it with **Java 21 Virtual Threads** and **StructuredTaskScope**, and writes
the results asynchronously with strictly bounded memory.

## Highlights

- **Virtual Threads + Structured Concurrency** — every stage (reader, workers, writer)
  runs as a subtask of a single `StructuredTaskScope.ShutdownOnFailure`.
- **Bounded memory / backpressure** — fixed-capacity `ArrayBlockingQueue`s propagate
  backpressure from the writer all the way back to the reader.
- **Immutable DTOs** — all cross-thread data objects are Java `record`s.
- **Spec-Driven Development** — [`intent.md`](intent.md), [`spec.md`](spec.md) and
  [`CLAUDE.md`](CLAUDE.md) are the single source of truth; ArchUnit enforces them in CI.

## Requirements

- JDK 21 (e.g. Eclipse Temurin 21)
- Maven 3.9+

The build enables preview features (`StructuredTaskScope`, `ScopedValue`) via
`--enable-preview` for both compilation and tests.

## Build and test

```bash
mvn clean verify
```

This runs all unit/integration tests plus ArchUnit architecture checks and enforces a
minimum 50% line-coverage gate. The heavy 10M-line memory stress test is tagged `stress`
and excluded by default.

## Run

```bash
mvn package
java --enable-preview -cp target/jvm-performance-simulator-1.0.0-SNAPSHOT.jar \
     com.jvmsim.Main --input data/input.csv --output data/output.csv
```

Alternatively, use `exec` with preview enabled:

```bash
mvn compile exec:java \
  -Dexec.mainClass=com.jvmsim.Main \
  -Dexec.args="--input data/input.csv --output data/output.csv"
```

### Options

| Option                | Default | Description                                      |
|-----------------------|---------|--------------------------------------------------|
| `-i`, `--input`       | —       | Input CSV file path (required)                   |
| `-o`, `--output`      | —       | Output CSV file path (required)                  |
| `--chunk-size`        | 5000    | Raw lines per chunk                              |
| `--workers`           | 8       | Worker virtual threads                           |
| `--queue-capacity`    | 32      | Bounded queue capacity (backpressure)            |
| `--gzip`              | false   | GZIP-compress the output                         |
| `--fail-fast`         | false   | Abort on the first malformed line                |
| `--level`             | —       | Comma-separated accepted levels (e.g. ERROR,WARN)|
| `--source`            | —       | Comma-separated accepted sources                 |
| `--min-value`         | —       | Inclusive lower bound for the numeric value      |
| `--max-value`         | —       | Inclusive upper bound for the numeric value      |
| `--value-scale`       | —       | Multiplication factor applied to values          |
| `--deadline-seconds`  | 0       | Whole-run deadline (0 = no deadline)             |

## Input format

Five-column CSV: `timestamp, level, source, message, value`. See
[`spec.md`](spec.md) for the full contract and the memory-budget rule.

## Memory stress gate

The pipeline must process 10 million lines under a fixed 256 MB heap:

```bash
mvn test -Dtest=MemoryStressTest -Dsurefire.excludedGroups=__none__
```

Lower the line count for a quick local check:

```bash
mvn test -Dtest=MemoryStressTest -Dsurefire.excludedGroups=__none__ -Dstress.lines=100000
```

## Project layout

- `src/main/java/com/jvmsim/model` — immutable records (DTOs)
- `src/main/java/com/jvmsim/io` — chunked reader
- `src/main/java/com/jvmsim/parse` — CSV parser + malformed-line policy
- `src/main/java/com/jvmsim/process` — filter, transformer, aggregator, chunk processor
- `src/main/java/com/jvmsim/concurrent` — `StructuredTaskScope` orchestrator
- `src/main/java/com/jvmsim/output` — ordered asynchronous writer
- `src/main/java/com/jvmsim/metrics` — Micrometer summary metrics
- `src/main/java/com/jvmsim/cli` — picocli command bootstrap
- `src/test/java` — unit, integration, ArchUnit and stress tests

## Future work

See [`docs/extra-features.md`](docs/extra-features.md) for the planned feature roadmap.
