package com.episteme.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object (DAO) for interacting with the 'files' and 'packages' tables.
 * <p>
 * This class encapsulates all raw SQL logic related to file tracking, ensuring
 * that business logic (like ProjectIndexer) remains decoupled from JDBC mechanics.
 */
public class FileDao {

    private final DatabaseManager dbManager;

    public FileDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Retrieves the stored hash for a given file path.
     * <p>
     * @implNote READS from the 'files' table.
     * 
     * @param path The relative or absolute path of the file.
     * @return The MD5/SHA-256 hash stored in the DB, or null if the file is not tracked.
     * @throws SQLException If the query fails.
     */
    public String getFileHash(String path) throws SQLException {
        String sql = "SELECT md5_hash FROM files WHERE path = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("md5_hash");
                }
            }
        }
        return null;
    }

    /**
     * Inserts or updates the hash for a specific file.
     * <p>
     * @implNote WRITES to the 'files' table.
     * 
     * @param path The path of the file.
     * @param hash The newly computed hash.
     * @throws SQLException If the update fails.
     */
    public void upsertFileHash(String path, String hash) throws SQLException {
        String sql = """
            INSERT INTO files (path, md5_hash) VALUES (?, ?)
            ON CONFLICT(path) DO UPDATE SET md5_hash = excluded.md5_hash
            """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            pstmt.setString(2, hash);
            pstmt.executeUpdate();
        }
    }
}
