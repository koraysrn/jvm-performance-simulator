# Extra Features Roadmap

This document lists enhancements that will make the project more useful or more
sophisticated. Items are intentionally **not implemented yet**; they are tracked here
for future iterations.

## Level 1 — Near-term polish

- [ ] **Zstd compression** in addition to GZIP for smaller, faster output.
- [ ] **Progress reporting** — processed-line percentage and estimated time remaining.
- [ ] **Summary report file** — write count/sum/min/max/avg and malformed-line stats to
      a sidecar JSON file.
- [ ] **Dead-letter output** — write malformed lines to a separate `.bad` file instead
      of only counting them.

## Level 2 — Mid-term capabilities

- [ ] **Checkpoint / resume** — persist the last committed chunk index so a crashed run
      can resume instead of restarting.
- [ ] **Multi-format input SPI** — JSONL, Parquet and Avro readers behind an
      `InputFormat` interface.
- [ ] **Schema inference** — derive column types from the first N lines.
- [ ] **Adaptive concurrency** — tune worker count from queue depth and CPU load.
- [ ] **Property-based testing (jqwik)** — prove the parser never crashes on arbitrary
      input.
- [ ] **Sampling mode** — fast preview of a huge file by processing every Nth line.
- [ ] **Metric export** — Prometheus endpoint or push gateway for the Micrometer metrics.

## Level 3 — Advanced / enterprise

- [ ] **GraalVM native image** — smaller footprint and faster startup.
- [ ] **LMAX Disruptor pipeline** — ring-buffer alternative for lower latency.
- [ ] **Web management panel** — start/stop jobs, watch metrics and queue depth live.
- [ ] **Multi-file processing** — process entire directories with per-file scopes.
- [ ] **Reactive comparison report** — benchmark Virtual Threads vs. Reactor/RxJava on
      identical workloads.
- [ ] **Spec-fuzz testing** — generate fuzz inputs directly from `spec.md` constraints.
- [ ] **Distributed mode** — partition work across multiple JVMs for terabyte-scale files.

## Design notes

- Every new concurrent feature MUST keep the structured-concurrency rule from
  [`CLAUDE.md`](../CLAUDE.md): all pipeline work inside `StructuredTaskScope`, bounded
  queues only, and exactly one writer per output stream.
- Memory-heavy options (byte-offset parallel reader, larger queues) MUST respect the
  memory-budget rule documented in [`spec.md`](../spec.md#81-memory-budget-rule).
