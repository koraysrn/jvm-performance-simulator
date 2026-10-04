package com.jvmsim;

import com.jvmsim.cli.CliOptions;
import picocli.CommandLine;

/**
 * Application entry point. Delegates to picocli for argument parsing and execution.
 */
public final class Main {

    private Main() {
        // Utility class: no instantiation.
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CliOptions()).execute(args);
        System.exit(exitCode);
    }
}
