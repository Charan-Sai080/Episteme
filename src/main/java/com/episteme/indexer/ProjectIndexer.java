package com.episteme.indexer;

import com.episteme.db.DatabaseManager;
import com.episteme.db.FileDao;
import spoon.Launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.stream.Stream;

/**
 * Orchestrates the incremental AST parsing using the Spoon compiler.
 * <p>
 * This class ensures that only new or modified Java files are passed into the Spoon
 * compiler by verifying file hashes against the database.
 */
public class ProjectIndexer {

    private final DatabaseManager dbManager;
    private final FileDao fileDao;

    /**
     * Constructs a ProjectIndexer with the provided database manager and DAO.
     */
    public ProjectIndexer(DatabaseManager dbManager, FileDao fileDao) {
        this.dbManager = dbManager;
        this.fileDao = fileDao;
    }

    /**
     * Scans the specified project root for Java files, filters out unmodified files,
     * groups them into batches, and triggers the AST generation for each batch.
     * <p>
     * @param projectRoot The root directory of the Java project to index.
     * @param batchSize The number of files to process in a single Spoon Launcher instance.
     * @throws SQLException If database interactions fail.
     * @throws IOException If file reading fails.
     */
    public void indexProject(File projectRoot, int batchSize) throws SQLException, IOException {
        java.util.Map<String, String> storedHashes = fileDao.getAllFileHashes();
        java.util.List<File> filesToProcess = new java.util.ArrayList<>();
        java.util.Map<File, String> newHashes = new java.util.HashMap<>();
        
        try (Stream<Path> paths = Files.walk(projectRoot.toPath())) {
            paths.filter(p -> p.toFile().isFile() && p.toFile().getName().endsWith(".java"))
                 .forEach(p -> {
                     try {
                         File file = p.toFile();
                         String currentHash = FileHashUtil.computeHash(file);
                         String storedHash = storedHashes.get(file.getAbsolutePath());
                         
                         if (storedHash == null || !storedHash.equals(currentHash)) {
                             filesToProcess.add(file);
                             newHashes.put(file, currentHash);
                         }
                     } catch (IOException e) {
                         throw new RuntimeException("Failed to read file for hashing", e);
                     }
                 });
        }
        
        if (filesToProcess.isEmpty()) {
            return;
        }

        // Process in batches
        for (int i = 0; i < filesToProcess.size(); i += batchSize) {
            int end = Math.min(i + batchSize, filesToProcess.size());
            java.util.List<File> batch = filesToProcess.subList(i, end);
            
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            // Reverting to compliance level 17 because Spoon 10.4.0 throws
            // IllegalArgumentException: Unrecognized option : -21 when using 21.
            launcher.getEnvironment().setComplianceLevel(17);
            
            for (File file : batch) {
                launcher.addInputResource(file.getAbsolutePath());
            }
            
            launcher.buildModel();
            
            // Note: AST Contract extraction logic will go here for Phase 1.5
            // ContractExtractor extractor = new ContractExtractor(dbManager, fileDao.getSymbolDao());
            // launcher.getModel().getRootPackage().accept(extractor);
            
            for (File file : batch) {
                fileDao.upsertFileHash(file.getAbsolutePath(), newHashes.get(file));
            }
            
            launcher = null; // explicit GC hint
        }
    }
}
