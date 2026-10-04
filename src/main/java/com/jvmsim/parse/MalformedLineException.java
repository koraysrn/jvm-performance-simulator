package com.jvmsim.parse;

/**
 * Thrown when the input contains a line that violates the CSV schema and the active
 * {@link MalformedLinePolicy} is {@code FAIL_FAST}.
 */
public class MalformedLineException extends RuntimeException {

    public MalformedLineException(String message) {
        super(message);
    }

    public MalformedLineException(String message, Throwable cause) {
        super(message, cause);
    }
}
