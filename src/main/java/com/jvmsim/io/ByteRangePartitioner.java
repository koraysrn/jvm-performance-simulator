package com.jvmsim.io;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Splits a file into byte ranges whose boundaries are aligned to line ends.
 *
 * <p>Each returned {@link ByteRange} starts exactly at the beginning of a line and ends
 * right after a newline (or at EOF for the last part). This lets multiple readers scan
 * disjoint regions in parallel without losing or duplicating a single line.</p>
 */
public final class ByteRangePartitioner {

    private ByteRangePartitioner() {
        // Utility class: no instantiation.
    }

    /**
     * Partitions the file into {@code parts} aligned ranges.
     *
     * @param path  input file
     * @param parts number of desired partitions
     * @return one range per partition (fewer if the file is empty)
     * @throws IOException when the file cannot be read
     */
    public static List<ByteRange> partition(Path path, int parts) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        if (parts <= 0) {
            throw new IllegalArgumentException("parts must be positive");
        }

        long size = Files.size(path);
        if (size == 0 || parts == 1) {
            return List.of(new ByteRange(0, 0, size));
        }

        List<ByteRange> ranges = new ArrayList<>(parts);
        long blockSize = size / parts;
        long start = 0;
        for (int i = 0; i < parts; i++) {
            long end;
            if (i == parts - 1) {
                end = size;
            } else {
                // Push the boundary forward to the next newline so the next part
                // starts at a clean line boundary.
                end = alignToLineEnd(path, start + blockSize);
            }
            ranges.add(new ByteRange(i, start, end));
            start = end;
        }
        return ranges;
    }

    /** Returns the position just after the first newline at or after {@code offset}. */
    private static long alignToLineEnd(Path path, long offset) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(offset);
            int b;
            while ((b = file.read()) != -1) {
                if (b == '\n') {
                    return file.getFilePointer();
                }
            }
            return file.length();
        }
    }
}
