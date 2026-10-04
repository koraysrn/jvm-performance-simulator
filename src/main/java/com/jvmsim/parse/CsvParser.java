package com.jvmsim.parse;

import com.jvmsim.model.LogRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stateless, thread-safe CSV decoder.
 *
 * <p>The parser converts raw input lines into immutable {@link LogRecord} instances
 * using the fixed schema {@code timestamp, level, source, message, value}. Apache
 * Commons CSV handles RFC-4180 quoting, so embedded delimiters and escaped quotes are
 * decoded correctly.</p>
 *
 * <p>The hot path uses {@link #parseChunk(List, long)}, which builds a single
 * {@link CSVParser} per chunk instead of one parser per line. This dramatically reduces
 * short-lived object allocation (GC churn) for multi-million-line files. The per-line
 * {@link #parseLine(String, long)} method remains available for small call sites and
 * unit tests.</p>
 *
 * <p>Instances are safe to share across worker virtual threads: the CSV format is
 * immutable and the malformed counter is an {@link AtomicLong}.</p>
 */
public final class CsvParser {

    private static final int EXPECTED_COLUMNS = 5;

    /** Shared, immutable CSV format. Surrounding spaces are trimmed for robustness. */
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .build();

    private final MalformedLinePolicy policy;
    private final AtomicLong malformedCount = new AtomicLong();

    public CsvParser(MalformedLinePolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        this.policy = policy;
    }

    /** Total number of malformed lines seen so far across all worker threads. */
    public long malformedCount() {
        return malformedCount.get();
    }

    /**
     * Decodes a single line. Prefer {@link #parseChunk(List, long)} on the hot path.
     *
     * @param line        raw line content (never null)
     * @param lineNumber  1-based source line number, used for diagnostics only
     * @return the decoded record, or {@code null} when the line is blank or skipped
     * @throws MalformedLineException when the policy is {@code FAIL_FAST} and decoding fails
     */
    public LogRecord parseLine(String line, long lineNumber) {
        // Blank lines carry no information and are silently ignored (not counted as malformed).
        if (line == null || line.isEmpty()) {
            return null;
        }

        // Strip a UTF-8 BOM present at the very beginning of the file.
        if (lineNumber == 1 && line.charAt(0) == '\uFEFF') {
            line = line.substring(1);
        }
        if (line.isEmpty()) {
            return null;
        }

        try (CSVParser parser = CSVParser.parse(line, FORMAT)) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                return null;
            }
            return decode(records.get(0), lineNumber);
        } catch (IOException | IllegalArgumentException e) {
            return handleMalformed(lineNumber, e.getMessage());
        }
    }

    /**
     * Decodes a whole chunk with a single parser instance.
     *
     * <p>Blank lines are skipped without being counted as malformed. The caller passes
     * the 1-based line number of the chunk's first raw line so that diagnostics keep
     * meaningful positions. Records are single-line by spec, so each parser record maps
     * one-to-one to a non-blank input line.</p>
     *
     * @param lines     raw lines belonging to one chunk
     * @param startLine 1-based line number of {@code lines[0]}
     * @return decoded records (blank and skipped-malformed lines omitted)
     * @throws MalformedLineException when the policy is {@code FAIL_FAST} and decoding fails
     */
    public List<LogRecord> parseChunk(List<String> lines, long startLine) {
        if (lines.isEmpty()) {
            return List.of();
        }

        StringBuilder joined = new StringBuilder(lines.size() * 64);
        // Primitive long array avoids boxing a Long object per line (GC churn).
        long[] lineNumbers = new long[lines.size()];
        int lineCount = 0;

        long lineNumber = startLine;
        boolean first = true;
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                lineNumber++;
                continue;
            }
            if (first && startLine == 1 && line.charAt(0) == '\uFEFF') {
                line = line.substring(1);
            }
            first = false;
            if (line.isEmpty()) {
                lineNumber++;
                continue;
            }
            joined.append(line).append('\n');
            lineNumbers[lineCount++] = lineNumber;
            lineNumber++;
        }

        if (lineCount == 0) {
            return List.of();
        }

        List<LogRecord> records = new ArrayList<>(lineCount);
        try (CSVParser parser = CSVParser.parse(joined.toString(), FORMAT)) {
            int index = 0;
            for (CSVRecord record : parser) {
                long recordLine = lineNumbers[Math.min(index, lineCount - 1)];
                index++;
                LogRecord decoded = decode(record, recordLine);
                if (decoded != null) {
                    records.add(decoded);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            // Structural parser failure (e.g. unterminated quote) counts as one malformed
            // chunk; with FAIL_FAST it aborts the whole pipeline.
            malformedCount.incrementAndGet();
            if (policy == MalformedLinePolicy.FAIL_FAST) {
                throw new MalformedLineException(
                        "Malformed CSV in chunk starting at line " + startLine + ": "
                                + sanitize(e.getMessage()), e);
            }
        }
        return records;
    }

    private LogRecord decode(CSVRecord record, long lineNumber) {
        if (record.size() != EXPECTED_COLUMNS) {
            return handleMalformed(lineNumber,
                    "expected " + EXPECTED_COLUMNS + " columns but found " + record.size());
        }

        final double value;
        try {
            value = Double.parseDouble(record.get(4));
        } catch (NumberFormatException e) {
            return handleMalformed(lineNumber, "non-numeric value: '" + record.get(4) + "'");
        }

        return new LogRecord(
                record.get(0),
                record.get(1),
                record.get(2),
                record.get(3),
                value);
    }

    private LogRecord handleMalformed(long lineNumber, String reason) {
        malformedCount.incrementAndGet();
        if (policy == MalformedLinePolicy.FAIL_FAST) {
            // Sanitize to prevent log injection (CWE-117) via embedded CR/LF characters.
            throw new MalformedLineException(
                    "Malformed CSV at line " + lineNumber + ": " + sanitize(reason));
        }
        return null;
    }

    /** Replaces control characters so untrusted input cannot forge log lines. */
    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\p{Cntrl}", " ");
    }
}
