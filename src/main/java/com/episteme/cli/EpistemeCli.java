package com.episteme.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * The main entry point for the Episteme Orchestration Engine CLI.
 * This class handles all terminal commands (e.g., 'index', 'query', 'scan').
 */
@Command(
    name = "episteme",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Episteme: AI Codebase Orchestration & Knowledge Graph Engine",
    subcommands = { IndexCommand.class, WebUiCommand.class }
)
public class EpistemeCli implements Callable<Integer> {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new EpistemeCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        System.out.println("Episteme Engine Initialized.");
        System.out.println("Use --help to see available commands.");
        // We will delegate to subcommands (index, query, catalog, scan) in the future.
        return 0;
    }
}
