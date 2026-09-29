package com.episteme.core.export;

import com.episteme.db.DatabaseManager;
import com.episteme.dto.export.ExportEdgeDTO;
import com.episteme.dto.export.ExportNodeDTO;
import com.episteme.dto.export.GraphExportDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class GraphExportService {

    public GraphExportDTO exportEntireGraph(DatabaseManager dbManager) throws SQLException {
        String nodeQuery = """
            SELECT id, kind, name, fqn
            FROM symbols
            """;
        String edgeQuery = """
            SELECT from_symbol, to_symbol, edge_type
            FROM edges
            """;

        try (Connection conn = dbManager.getConnection()) {
            int nodeCount = 0;
            int edgeCount = 0;
            
            try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM symbols");
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) nodeCount = rs.getInt(1);
            }
            try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM edges");
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) edgeCount = rs.getInt(1);
            }

            List<ExportNodeDTO> nodes = new ArrayList<>(nodeCount);
            List<ExportEdgeDTO> edges = new ArrayList<>(edgeCount);

            try (PreparedStatement stmt = conn.prepareStatement(nodeQuery);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String id = String.valueOf(rs.getInt("id"));
                    String fqn = rs.getString("fqn");
                    String name = rs.getString("name");
                    String label = (fqn != null && !fqn.isEmpty()) ? fqn : name;
                    String type = rs.getString("kind");
                    nodes.add(new ExportNodeDTO(id, label, type));
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(edgeQuery);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String source = String.valueOf(rs.getInt("from_symbol"));
                    String target = String.valueOf(rs.getInt("to_symbol"));
                    String type = rs.getString("edge_type");
                    edges.add(new ExportEdgeDTO(source, target, type));
                }
            }

            return new GraphExportDTO(nodes, edges);
        }
    }
}
