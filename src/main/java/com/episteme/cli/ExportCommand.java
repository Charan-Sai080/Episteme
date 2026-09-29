package com.episteme.cli;

import com.episteme.core.export.GraphExportService;
import com.episteme.db.DatabaseManager;
import com.episteme.dto.export.GraphExportDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "export", description = "Exports the database graph to JSON")
public class ExportCommand implements Callable<Integer> {

    @Option(names = {"-d", "--db"}, description = "Database file path", defaultValue = "graph.db")
    private Path dbPath;

    @Option(names = {"-o", "--out"}, description = "Output JSON file path", required = true)
    private Path outPath;

    @Override
    public Integer call() throws Exception {
        DatabaseManager dbManager = new DatabaseManager();
        try {
            if (outPath.getParent() != null) {
                java.nio.file.Files.createDirectories(outPath.getParent());
            }
            
            dbManager.initialize(dbPath.toString());
            GraphExportService exportService = new GraphExportService();
            GraphExportDTO dto = exportService.exportEntireGraph(dbManager);
            
            ObjectMapper mapper = new ObjectMapper();
            mapper.findAndRegisterModules();
            mapper.writeValue(outPath.toFile(), dto);
            
            System.out.println("Export successful: " + outPath.toString());
            return 0;
        } finally {
            dbManager.close();
        }
    }
}
