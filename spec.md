# Technical Specification

## 1. Scope

A command-line batch pipeline that reads one input file, processes it and writes one
output file. The MVP runs on a single JVM using Java 21 virtual threads and structured
concurrency.

## 2. Input format

- Text file, UTF-8 encoded, optionally with a UTF-8 BOM (BOM is stripped by the parser).
- CSV with a fixed five-column schema: `timestamp, level, source, message, value`.
- Fields are RFC-4180 quoted when they contain a delimiter, a quote or a newline.
- Records are single-line; embedded newlines are not supported in the MVP.
- Blank lines are ignored and not counted as malformed.

| Column    | Type   | Example                    |
|-----------|--------|----------------------------|
| timestamp | String | `2026-10-04T20:00:00Z`     |
| level     | String | `INFO`, `WARN`, `ERROR`    |
| source    | String | `auth-service`             |
| message   | String | `login ok`                 |
| value     | double | `42.5`                     |

## 3. Output format

- Same five-column CSV layout, one record per line, `\n` line separator.
- Output preserves the input order of records that passed the filter.
- Optional GZIP compression via `--gzip`.

## 4. Pipeline stages

1. **Partitioner** — splits the file into byte ranges aligned to line boundaries
   (`ByteRangePartitioner`).
2. **Readers** — one virtual thread per range scans its region in parallel and groups raw
   lines into fixed-size `Chunk` objects (`--chunk-size`).
3. **Merger** — consumes reader queues in partition order, assigns global sequence ids and
   feeds the worker queue, preserving input order.
4. **Parser** — worker threads decode raw lines into immutable `LogRecord` instances.
5. **Processor** — each worker applies filter -> transform -> aggregate and renders
   surviving records to CSV lines.
6. **Writer** — a single virtual thread writes `ProcessedChunk` results in sequence order.

## 5. Concurrency model

- One `StructuredTaskScope.ShutdownOnFailure` per run, using a named
  `Thread.ofVirtual().factory()`.
- Subtasks: N readers (N = `--workers`), 1 merger, `--workers` workers, 1 writer.
- Every reader has a bounded `ArrayBlockingQueue`; the merger drains them in partition
  order into a bounded worker queue, and workers feed a bounded writer queue. When any
  queue is full, its producer blocks (backpressure).
- Worker loop terminates on a poison-pill (`Optional.empty()`); the last worker signals
  end-of-input to the writer.
- A run id is propagated to all subtasks via `ScopedValue`.
- Optional whole-run deadline via `--deadline-seconds` using `joinUntil`.

## 6. Error handling

- `MalformedLinePolicy.SKIP` (default): decode failures are counted and dropped.
- `MalformedLinePolicy.FAIL_FAST`: the first decode failure throws
  `MalformedLineException`, the scope shuts down and all sibling tasks are cancelled.
- Writer I/O failure propagates as an unchecked exception and cancels the whole scope.
- Interruptions cancel the scope cleanly; no orphan threads remain after the run.

## 7. Aggregation semantics

- Per-chunk partials are merged with a commutative, associative merge, so the result is
  independent of chunk partitioning.
- `count` uses overflow-checked `Math.addExact`.
- `min`/`max`/`sum`/`avg` follow `double` semantics; empty input yields
  `min=+Inf`, `max=-Inf`, `avg=NaN`.

## 8. Defaults and limits

| Parameter           | Default | Constraint       |
|---------------------|---------|------------------|
| `--chunk-size`      | 2000    | > 0              |
| `--workers`         | 8       | > 0              |
| `--queue-capacity`  | 32      | > 0              |
| `--deadline-seconds`| 0       | >= 0 (0 = none)  |
| `--value-scale`     | null    | finite           |
| `--min-value`       | null    | <= `--max-value` |

## 8.1 Memory budget rule

The maximum number of live raw lines is bounded by:

```
live lines ~= chunkSize * (queueCapacity * (workerCount + 1) + workerCount)
```

The extra `+ 1` queue factor accounts for the per-reader queues introduced by parallel
byte-range reading. Operators MUST keep this product small enough for the configured
heap. With the defaults (2000 * (32 * 9 + 8) = 592k lines) the resident string data
stays well below `-Xmx256m`. Increasing `chunkSize` or `queueCapacity` requires
increasing the heap accordingly.

## 9. Acceptance criteria

1. A 10M-line file completes without `OutOfMemoryError` under a fixed `-Xmx256m`.
2. Output line count equals the number of records that passed the filter.
3. Output order matches input order.
4. Running the same input twice produces byte-identical output (determinism).
5. ArchUnit tests pass: layer boundaries respected, DTOs are records, no thread pools.
