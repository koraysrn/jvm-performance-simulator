package com.jvmsim.io;

/**
 * Immutable, half-open byte range {@code [start, end)} of the input file.
 *
 * @param index partition index, starting at zero
 * @param start inclusive start offset, aligned to a line boundary (0 for the first part)
 * @param end   exclusive end offset, aligned to a line boundary (file size for the last part)
 */
public record ByteRange(int index, long start, long end) {

    public ByteRange {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("invalid range bounds: [" + start + ", " + end + ")");
        }
    }

    /** Number of bytes covered by this range. */
    public long length() {
        return end - start;
    }
}
