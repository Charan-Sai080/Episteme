package com.episteme.db;

import com.episteme.dto.GraphNodeDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GraphQueryDaoTest {

    private DatabaseManager dbManager;
    private GraphQueryDao graphQueryDao;

    @BeforeEach
    public void setup() throws SQLException {
        dbManager = new DatabaseManager();
        dbManager.initialize(":memory:");
        graphQueryDao = new GraphQueryDao(dbManager);
        insertDummyData();
    }

    @AfterEach
    public void teardown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    private void insertDummyData() throws SQLException {
        try (Connection conn = dbManager.getConnection()) {
            // Insert dummy files (just to satisfy FK constraints if any, though not strictly required for symbols unless ON DELETE CASCADE)
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO files (id, path, md5_hash) VALUES (?, ?, ?)")) {
                pstmt.setInt(1, 1);
                pstmt.setString(2, "/dummy/path.java");
                pstmt.setString(3, "hash");
                pstmt.executeUpdate();
            }

            // Insert symbols
            String insertSymbol = "INSERT INTO symbols (id, file_id, kind, name, fqn, is_external) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSymbol)) {
                Object[][] symbols = {
                    {1, 1, "METHOD", "A", "pkg.A", false},
                    {2, 1, "METHOD", "B", "pkg.B", false},
                    {3, 1, "METHOD", "C", "pkg.C", false},
                    {4, 1, "METHOD", "D", "pkg.D", false},
                    {5, 1, "METHOD", "E", "pkg.E", false}
                };
                for (Object[] sym : symbols) {
                    pstmt.setInt(1, (Integer) sym[0]);
                    pstmt.setInt(2, (Integer) sym[1]);
                    pstmt.setString(3, (String) sym[2]);
                    pstmt.setString(4, (String) sym[3]);
                    pstmt.setString(5, (String) sym[4]);
                    pstmt.setBoolean(6, (Boolean) sym[5]);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }

            // Insert edges
            String insertEdge = "INSERT INTO edges (from_symbol, to_symbol, edge_type) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertEdge)) {
                Object[][] edges = {
                    {1, 2, "CALLS"},    // A calls B
                    {2, 3, "CALLS"},    // B calls C
                    {3, 1, "CALLS"},    // C calls A (Cycle)
                    {1, 4, "READS"},    // A reads D
                    {4, 5, "WRITES"}    // D writes E
                };
                for (Object[] edge : edges) {
                    pstmt.setInt(1, (Integer) edge[0]);
                    pstmt.setInt(2, (Integer) edge[1]);
                    pstmt.setString(3, (String) edge[2]);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
        }
    }

    @Test
    public void testFindDownstreamImpact_HandlesCycles() throws SQLException {
        // Find downstream from A with deep maxDepth
        List<GraphNodeDTO> impact = graphQueryDao.findDownstreamImpact(1, 10);
        
        // Expected traversal:
        // A -> B (CALLS) depth 1
        // A -> D (READS) depth 1
        // B -> C (CALLS) depth 2
        // C -> A (CALLS) depth 3 (Should be skipped due to cycle prevention, but A is the root so it might be skipped if we started with A. Since A is root, path is `,1,`, when C calls A, instr(path, `,1,`) is non-zero so it's excluded).
        // D -> E (WRITES) depth 2
        // Total expected nodes: B, D, C, E. (4 nodes)

        assertEquals(4, impact.size());

        boolean foundB = false, foundC = false, foundD = false, foundE = false;
        for (GraphNodeDTO node : impact) {
            if (node.name().equals("B")) { foundB = true; assertEquals(1, node.depth()); }
            if (node.name().equals("D")) { foundD = true; assertEquals(1, node.depth()); }
            if (node.name().equals("C")) { foundC = true; assertEquals(2, node.depth()); }
            if (node.name().equals("E")) { foundE = true; assertEquals(2, node.depth()); }
        }
        assertTrue(foundB && foundC && foundD && foundE);
    }

    @Test
    public void testFindDownstreamImpact_RespectsMaxDepth() throws SQLException {
        // Find downstream from A with maxDepth = 1
        List<GraphNodeDTO> impact = graphQueryDao.findDownstreamImpact(1, 1);
        
        // Should only find B and D
        assertEquals(2, impact.size());
        for (GraphNodeDTO node : impact) {
            assertTrue(node.name().equals("B") || node.name().equals("D"));
            assertEquals(1, node.depth());
        }
    }

    @Test
    public void testFindCallers_HandlesCycles() throws SQLException {
        // Insert some upstream edges
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO edges (from_symbol, to_symbol, edge_type) VALUES (?, ?, ?)")) {
            // E extended by F, F overridden by A (making upstream path E <- F <- A)
            pstmt.setInt(1, 6); pstmt.setInt(2, 5); pstmt.setString(3, "EXTENDS"); pstmt.executeUpdate();
            pstmt.setInt(1, 1); pstmt.setInt(2, 6); pstmt.setString(3, "OVERRIDES"); pstmt.executeUpdate();
            
            // Symbol F
            try (PreparedStatement p2 = conn.prepareStatement("INSERT INTO symbols (id, file_id, kind, name, fqn, is_external) VALUES (?, ?, ?, ?, ?, ?)")) {
                p2.setInt(1, 6); p2.setInt(2, 1); p2.setString(3, "CLASS"); p2.setString(4, "F"); p2.setString(5, "pkg.F"); p2.setBoolean(6, false);
                p2.executeUpdate();
            }
        }

        // Callers of C
        // A calls B, B calls C, C calls A
        // Upstream of C: B (depth 1), A (depth 2), C (depth 3 - cycle, should be ignored)
        List<GraphNodeDTO> callers = graphQueryDao.findCallers(3, 10);
        
        // Expect B and A. 
        // Wait, C calls A -> A is upstream of C? No, C points to A, so A is DOWNSTREAM of C. 
        // We want upstream of C: which is B (B calls C).
        // Then what is upstream of B? A (A calls B).
        // What is upstream of A? C (C calls A).
        // And upstream of C? B (already visited).
        
        assertEquals(2, callers.size());
        
        boolean foundB = false, foundA = false;
        for (GraphNodeDTO node : callers) {
            if (node.name().equals("B")) { foundB = true; assertEquals(1, node.depth()); }
            if (node.name().equals("A")) { foundA = true; assertEquals(2, node.depth()); }
        }
        assertTrue(foundB && foundA);
    }
    
    @Test
    public void testFindCallers_RespectsMaxDepth() throws SQLException {
        List<GraphNodeDTO> callers = graphQueryDao.findCallers(3, 1);
        
        // Upstream of C with depth 1: B
        assertEquals(1, callers.size());
        assertEquals("B", callers.get(0).name());
        assertEquals(1, callers.get(0).depth());
    }
}
