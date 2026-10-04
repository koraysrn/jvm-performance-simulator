package com.jvmsim.io;

import com.jvmsim.model.Chunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ByteRangePartitionerTest {

    @TempDir
    Path tempDir;

    @Test
    void partitionsCoverEveryLineExactlyOnce() throws Exception {
        Path file = tempDir.resolve("data.csv");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            lines.add("line-" + i + ",INFO,s,m," + i);
        }
        Files.writeString(file, String.join("\n", lines) + "\n");

        List<ByteRange> ranges = ByteRangePartitioner.partition(file, 7);
        assertEquals(7, ranges.size());

        List<String> collected = new ArrayList<>();
        for (ByteRange range : ranges) {
            try (RangeChunkReader reader = new RangeChunkReader(file, range, 50)) {
                Chunk chunk;
                while ((chunk = reader.readNext()) != null) {
                    collected.addAll(chunk.lines());
                }
            }
        }

        assertEquals(lines, collected);
    }

    @Test
    void partitionsFileWithoutTrailingNewline() throws Exception {
        Path file = tempDir.resolve("data.csv");
        Files.writeString(file, "a\nb\nc");

        List<ByteRange> ranges = ByteRangePartitioner.partition(file, 3);

        List<String> collected = new ArrayList<>();
        for (ByteRange range : ranges) {
            try (RangeChunkReader reader = new RangeChunkReader(file, range, 10)) {
                Chunk chunk;
                while ((chunk = reader.readNext()) != null) {
                    collected.addAll(chunk.lines());
                }
            }
        }

        assertEquals(List.of("a", "b", "c"), collected);
    }

    @Test
    void emptyFileYieldsSingleEmptyRange() throws Exception {
        Path file = tempDir.resolve("empty.csv");
        Files.writeString(file, "");

        List<ByteRange> ranges = ByteRangePartitioner.partition(file, 5);

        assertEquals(1, ranges.size());
        assertEquals(0, ranges.get(0).length());
    }
}
