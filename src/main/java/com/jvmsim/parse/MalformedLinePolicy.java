package com.jvmsim.parse;

/**
 * Defines how the parser reacts to a line that cannot be decoded into a valid record.
 */
public enum MalformedLinePolicy {

    /**
     * Skip the malformed line, increment the malformed counter and continue processing.
     * The pipeline keeps running and the final report shows how many lines were dropped.
     */
    SKIP,

    /**
     * Abort the whole pipeline immediately by throwing a {@link MalformedLineException}.
     * With structured concurrency this cancels all sibling tasks.
     */
    FAIL_FAST
}
