package com.episteme.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.util.concurrent.Callable;

/**
 * The 'serve' subcommand for the CLI.
 * <p>
 * Usage: impact-index serve --port=8080
 */
@Command(name = "serve", description = "Starts a local web UI to visualize the Knowledge Graph.")
public class WebUiCommand implements Callable<Integer> {

    @Option(names = {"-p", "--port"}, description = "Port for the local web server.", defaultValue = "8080")
    private int port;
    
    @Option(names = {"-d", "--db"}, description = "Path to the SQLite database file.", defaultValue = "graph.db")
    private String dbPath;

    @Override
    public Integer call() throws Exception {
        System.out.println("Starting Episteme Visualizer Web UI on port " + port);
        System.out.println("Loading graph data from: " + dbPath);
        
        // Contract Only: In Phase 2, this will spin up a lightweight Jetty/Undertow server 
        // to serve an HTML/JS frontend (D3.js or Cytoscape) that queries SQLite.
        System.out.println("Web UI scaffolded. Implementation pending.");
        
        return 0;
    }
}
