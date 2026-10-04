# CLAUDE.md — Corporate Memory & Strict Rules

This file defines non-negotiable rules for every AI agent and human developer working
on this project. Violations are enforced by ArchUnit tests in CI.

## Language

- All code, comments, log messages, commit messages and documentation MUST be English.

## Java & API rules

- Java 21 with `--enable-preview` is required (StructuredTaskScope, ScopedValue).
- Every DTO crossing thread boundaries MUST be a Java `record`.
- Mutable shared state is forbidden in the concurrent path. Prefer
  `AtomicReference`/`AtomicLong` merges and immutable records.

## Concurrency rules

- All concurrent work MUST be wrapped in `StructuredTaskScope` (prefer
  `ShutdownOnFailure`); never use `Executors.newFixedThreadPool` or raw `Thread` for the
  pipeline stages.
- All producer/consumer queues MUST be bounded (`ArrayBlockingQueue` or equivalent).
  Backpressure is mandatory.
- There MUST be exactly one writer per output stream; workers never write files directly.
- Scopes MUST be opened with try-with-resources so no thread escapes the scope.

## Memory rules

- Never load the whole input file into memory; process in chunks.
- Never buffer processed results unboundedly; the writer orders chunks with a bounded
  in-memory map.

## Architecture rules (enforced by ArchUnit)

- `model` package may depend on nothing outside `java.*`.
- `io` and `parse` packages may depend on `model` but NOT on `process`, `concurrent` or
  `output`.
- `process` may depend on `model` only.
- `concurrent` may depend on `model`, `io`, `parse`, `process`, `output`.
- `output` may depend on `model` only.
- `cli` and `metrics` may depend on all application packages.

## Testing rules

- Every public behavior needs a unit test; every phase needs integration coverage.
- Golden-master files are committed and byte-compared in CI.
- The 10M-line memory stress test MUST run in CI (or an equivalent bounded-memory gate).

## Definition of done

- `mvn clean verify` passes, including ArchUnit, JaCoCo thresholds and the stress gate.
