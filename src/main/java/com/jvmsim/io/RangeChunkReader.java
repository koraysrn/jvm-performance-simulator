package com.jvmsim.io;

import com.jvmsim.model.Chunk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads a single {@link ByteRange} of the input file as UTF-8 text and groups its lines
 * into fixed-size {@link Chunk}s.
 *
 * <p>Reading is bounded by the range length: a positional {@link FileChannel} read never
 * crosses into the next partition, so parallel readers cannot steal each other's lines.</p>
 */
public final class RangeChunkReader implements AutoCloseable {

    private final BufferedReader reader;
    private final int chunkSize;
    private int nextId;
    private long linesRead;

    public RangeChunkReader(Path path, ByteRange range, int chunkSize) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(range, "range must not be null");
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }

        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        InputStream bounded = new PositionInputStream(
                channel, range.start(), range.end() - range.start());
        this.reader = new BufferedReader(
                new InputStreamReader(bounded, StandardCharsets.UTF_8), 8192);
        this.chunkSize = chunkSize;
    }

    /**
     * Reads the next chunk of this range, or {@code null} when the range is exhausted.
     *
     * @return the next bounded chunk, or {@code null} at end of range
     * @throws IOException when an I/O error occurs while reading
     */
    public Chunk readNext() throws IOException {
        List<String> lines = new ArrayList<>(chunkSize);
        String line;
        while (lines.size() < chunkSize && (line = reader.readLine()) != null) {
            linesRead++;
            lines.add(line);
        }
        if (lines.isEmpty()) {
            return null;
        }
        // startLine is 1-based within this range; the orchestrator assigns a global
        // sequence id and only uses startLine for diagnostics.
        long startLine = linesRead - lines.size() + 1;
        return new Chunk(nextId++, startLine, lines);
    }

    /** Number of lines read from this range so far. */
    public long linesRead() {
        return linesRead;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    /**
     * Bounded view over a region of a {@link FileChannel}, using positional reads so the
     * channel's own position is never mutated and parallel readers stay independent.
     */
    private static final class PositionInputStream extends InputStream {

        private final FileChannel channel;
        private long position;
        private long remaining;

        PositionInputStream(FileChannel channel, long start, long length) {
            this.channel = channel;
            this.position = start;
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            ByteBuffer one = ByteBuffer.allocate(1);
            int read = channel.read(one, position);
            if (read < 0) {
                return -1;
            }
            position++;
            remaining--;
            return one.get(0) & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(length, remaining);
            ByteBuffer wrapped = ByteBuffer.wrap(buffer, offset, toRead);
            int read = channel.read(wrapped, position);
            if (read < 0) {
                return -1;
            }
            position += read;
            remaining -= read;
            return read;
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }
}
