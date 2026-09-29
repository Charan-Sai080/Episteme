package com.episteme.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.util.concurrent.Callable;

/**
 * The 'index' subcommand for the CLI.
 * <p>
 * Usage: impact-index index --project=/path/to/project
 */
@Command(name = "index", description = "Indexes a Java project into the SQLite Knowledge Graph.")
public class IndexCommand implements Callable<Integer> {

    @Option(names = {"-p", "--project"}, description = "Path to the root of the Java project.", required = true)
    private java.nio.file.Path projectPath;
    
    @Option(names = {"-d", "--db"}, description = "Path to the SQLite database file.", defaultValue = "graph.db")
    private String dbPath;

    @Option(names = {"-b", "--batch-size"}, description = "Number of files to process per batch.", defaultValue = "500")
    private int batchSize;

    /**
     * Executes the indexation process by wiring up the DatabaseManager and ProjectIndexer.
     * <p>
     * @implNote WRITES to the SQLite DB specified by dbPath.
     * 
     * @return 0 on success, non-zero on failure.
     */
    @Override
    public Integer call() throws Exception {
        System.out.println("Initializing Episteme Engine at: " + projectPath);
        
        java.io.File projectDir = projectPath.toFile();
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            System.err.println("Error: Project path does not exist or is not a directory: " + projectPath);
            return 1;
        }

        java.nio.file.Path dbParent = java.nio.file.Path.of(dbPath).getParent();
        if (dbParent != null) {
            java.nio.file.Files.createDirectories(dbParent);
        }
        
        com.episteme.db.DatabaseManager dbManager = new com.episteme.db.DatabaseManager();
        try {
            dbManager.initialize(dbPath);
            com.episteme.db.FileDao fileDao = new com.episteme.db.FileDao(dbManager);
            
            com.episteme.indexer.ProjectIndexer indexer = new com.episteme.indexer.ProjectIndexer(dbManager, fileDao);
            
            indexer.indexProject(projectDir, batchSize);
            System.out.println("Indexing completed successfully. Knowledge Graph saved to: " + dbPath);
            return 0;
            
        } finally {
            dbManager.close();
        }
    }
}
