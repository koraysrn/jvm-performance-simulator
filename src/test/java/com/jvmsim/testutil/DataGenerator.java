package com.jvmsim.testutil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Deterministic CSV test-data generator.
 *
 * <p>Given the same seed the output is byte-identical, which makes end-to-end golden
 * tests reproducible across machines and CI runs.</p>
 */
public final class DataGenerator {

    private static final String[] LEVELS = {"INFO", "WARN", "ERROR", "DEBUG", "TRACE"};
    private static final String[] SOURCES = {"auth-service", "payment-service", "gateway", "inventory"};

    private DataGenerator() {
        // Utility class: no instantiation.
    }

    /** Renders one deterministic CSV line for the given 0-based index. */
    public static String line(long index, Random random) {
        String timestamp = "2026-10-04T"
                + pad(index % 24) + ":"
                + pad((index / 24) % 60) + ":"
                + pad(index % 60) + "Z";
        String level = LEVELS[(int) Math.floorMod(index, LEVELS.length)];
        String source = SOURCES[(int) Math.floorMod(index, SOURCES.length)];
        String message = "message-" + index;
        double value = random.nextDouble() * 2000.0 - 1000.0;
        return String.join(",", timestamp, level, source, message, Double.toString(value));
    }

    /** Writes {@code count} deterministic lines to the given path. */
    public static void generate(Path path, long count, long seed) throws IOException {
        Random random = new Random(seed);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (long i = 0; i < count; i++) {
                writer.write(line(i, random));
                writer.newLine();
            }
        }
    }

    private static String pad(long value) {
        return value < 10 ? "0" + value : Long.toString(value);
    }
}
