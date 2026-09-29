package com.episteme.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Knowledge Graph Edges.
 * <p>
 * Responsible for batch-inserting edges (CALLS, READS, WRITES, EXTENDS, IMPLEMENTS) 
 * into the SQLite `edges` table.
 */
public class EdgeDao {

    private final DatabaseManager dbManager;
    private final List<EdgeRecord> batchQueue;
    private static final int BATCH_SIZE = 1000;

    public EdgeDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.batchQueue = new ArrayList<>();
    }

    /**
     * Queues an edge for insertion. Flushes to DB if the batch size is met.
     */
    public void addEdge(long sourceId, long targetId, String type) throws SQLException {
        if (sourceId <= 0 || targetId <= 0) return; // Invalid edge
        
        batchQueue.add(new EdgeRecord(sourceId, targetId, type));
        if (batchQueue.size() >= BATCH_SIZE) {
            flush();
        }
    }

    /**
     * Flushes the remaining queued edges into the database.
     */
    public void flush() throws SQLException {
        if (batchQueue.isEmpty()) return;

        String insertSql = "INSERT INTO edges (from_symbol, to_symbol, edge_type) VALUES (?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            
            // SQLite optimization for bulk inserts
            conn.setAutoCommit(false);
            try {
                for (EdgeRecord edge : batchQueue) {
                    pstmt.setLong(1, edge.sourceId());
                    pstmt.setLong(2, edge.targetId());
                    pstmt.setString(3, edge.type());
                    pstmt.addBatch();
                }
                
                pstmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } finally {
            batchQueue.clear();
        }
    }

    private record EdgeRecord(long sourceId, long targetId, String type) {}
}
