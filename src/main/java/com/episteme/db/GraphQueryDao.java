package com.episteme.db;

import com.episteme.dto.GraphNodeDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for querying Knowledge Graph.
 */
public class GraphQueryDao {

    private final DatabaseManager dbManager;

    public GraphQueryDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Finds downstream impact of a symbol.
     */
    public List<GraphNodeDTO> findDownstreamImpact(long symbolId, int maxDepth) throws SQLException {
        String sql = """
            WITH RECURSIVE downstream_search(symbol_id, depth, edge_type, path) AS (
                SELECT 
                    id, 
                    0, 
                    'START', 
                    ',' || id || ',' 
                FROM symbols WHERE id = ?
                
                UNION ALL
                
                SELECT 
                    e.to_symbol,
                    ds.depth + 1,
                    e.edge_type,
                    ds.path || e.to_symbol || ','
                FROM edges e
                JOIN downstream_search ds ON e.from_symbol = ds.symbol_id
                WHERE e.edge_type IN ('CALLS', 'READS', 'WRITES')
                  AND ds.depth < ?
                  AND instr(ds.path, ',' || e.to_symbol || ',') = 0
            )
            SELECT 
                ds.symbol_id, 
                s.name, 
                s.fqn, 
                s.kind, 
                ds.depth, 
                ds.edge_type
            FROM downstream_search ds
            JOIN symbols s ON ds.symbol_id = s.id
            WHERE ds.depth > 0
            ORDER BY ds.depth ASC
        """;

        List<GraphNodeDTO> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, symbolId);
            pstmt.setInt(2, maxDepth);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new GraphNodeDTO(
                        rs.getLong("symbol_id"),
                        rs.getString("name"),
                        rs.getString("fqn"),
                        rs.getString("kind"),
                        rs.getInt("depth"),
                        rs.getString("edge_type")
                    ));
                }
            }
        }
        return results;
    }

    /**
     * Finds callers (upstream dependencies) of a symbol.
     */
    public List<GraphNodeDTO> findCallers(long symbolId, int maxDepth) throws SQLException {
        String sql = """
            WITH RECURSIVE upstream_search(symbol_id, depth, edge_type, path) AS (
                SELECT 
                    id, 
                    0, 
                    'START', 
                    ',' || id || ',' 
                FROM symbols WHERE id = ?
                
                UNION ALL
                
                SELECT 
                    e.from_symbol,
                    us.depth + 1,
                    e.edge_type,
                    us.path || e.from_symbol || ','
                FROM edges e
                JOIN upstream_search us ON e.to_symbol = us.symbol_id
                WHERE e.edge_type IN ('CALLS', 'EXTENDS', 'OVERRIDES')
                  AND us.depth < ?
                  AND instr(us.path, ',' || e.from_symbol || ',') = 0
            )
            SELECT 
                us.symbol_id, 
                s.name, 
                s.fqn, 
                s.kind, 
                us.depth, 
                us.edge_type
            FROM upstream_search us
            JOIN symbols s ON us.symbol_id = s.id
            WHERE us.depth > 0
            ORDER BY us.depth ASC
        """;

        List<GraphNodeDTO> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, symbolId);
            pstmt.setInt(2, maxDepth);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new GraphNodeDTO(
                        rs.getLong("symbol_id"),
                        rs.getString("name"),
                        rs.getString("fqn"),
                        rs.getString("kind"),
                        rs.getInt("depth"),
                        rs.getString("edge_type")
                    ));
                }
            }
        }
        return results;
    }
}
