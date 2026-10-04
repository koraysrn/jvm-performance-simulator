package com.jvmsim.io;

import com.jvmsim.model.Chunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsChunksWithIdsAndStartLines() throws Exception {
        Path file = tempDir.resolve("input.csv");
        Files.writeString(file, "a\nb\nc\nd\ne\n");

        try (ChunkReader reader = new ChunkReader(file, 2)) {
            Chunk first = reader.readNext();
            assertEquals(0, first.id());
            assertEquals(1, first.startLine());
            assertEquals(List.of("a", "b"), first.lines());

            Chunk second = reader.readNext();
            assertEquals(1, second.id());
            assertEquals(3, second.startLine());
            assertEquals(List.of("c", "d"), second.lines());

            Chunk third = reader.readNext();
            assertEquals(2, third.id());
            assertEquals(5, third.startLine());
            assertEquals(List.of("e"), third.lines());

            assertNull(reader.readNext());
            assertEquals(5, reader.linesRead());
        }
    }

    @Test
    void emptyFileYieldsNullImmediately() throws Exception {
        Path file = tempDir.resolve("empty.csv");
        Files.writeString(file, "");

        try (ChunkReader reader = new ChunkReader(file, 10)) {
            assertNull(reader.readNext());
            assertEquals(0, reader.linesRead());
        }
    }

    @Test
    void crlfLineEndingsAreStripped() throws Exception {
        Path file = tempDir.resolve("crlf.csv");
        Files.writeString(file, "a\r\nb\r\n");

        try (ChunkReader reader = new ChunkReader(file, 10)) {
            Chunk chunk = reader.readNext();
            assertEquals(List.of("a", "b"), chunk.lines());
        }
    }

    @Test
    void rejectsNonPositiveChunkSize() {
        Path file = tempDir.resolve("any.csv");
        assertThrows(IllegalArgumentException.class, () -> new ChunkReader(file, 0));
    }

    @Test
    void failsFastOnMissingFile() {
        Path missing = tempDir.resolve("missing.csv");
        assertThrows(IOException.class, () -> new ChunkReader(missing, 10));
    }
}
