# Intent

## Problem Statement

We have a very large, complex log/CSV file (assumed to be 10 million lines or more).
Reading it fully into memory is not feasible, and processing it with a single thread is
too slow. We need an application that:

1. Reads the file in chunks instead of loading it all at once.
2. Processes each chunk (filtering, transformation and mathematical aggregation).
3. Writes the results to another file asynchronously.

## Goals

- Process a 10M+ line file with the lowest possible CPU and RAM consumption.
- Keep memory usage bounded and fluid; never fail with `OutOfMemoryError` due to
  unbounded buffering.
- Exploit Java 21 **Virtual Threads** and **StructuredTaskScope** for all concurrency.
- Use immutable **Records** for every data-transfer object crossing thread boundaries.
- Produce deterministic, ordered output regardless of worker scheduling.

## Constraints (hard rules)

1. **No traditional thread pools.** Do not use `Executors.newFixedThreadPool`,
   `ThreadPoolExecutor` or platform-thread pools. All concurrency must go through
   `StructuredTaskScope` on virtual threads.
2. **Bounded memory.** All producer/consumer queues must be bounded; backpressure must
   propagate from the writer back to the reader.
3. **Immutable DTOs.** Every cross-thread data object must be a `record`.
4. **Spec-driven development.** `intent.md`, `spec.md` and `CLAUDE.md` are the single
   source of truth; code that violates them must fail CI via ArchUnit.
5. **All code, comments, logs and documentation are written in English.**

## Non-goals (for the MVP)

- Distributed/partitioned processing across multiple machines.
- Real-time streaming from network sources.
- A graphical user interface.
