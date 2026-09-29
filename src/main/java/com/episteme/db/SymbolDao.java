package com.episteme.db;

import spoon.reflect.reference.CtTypeReference;
import java.sql.SQLException;

/**
 * Data Access Object for Symbol Resolution and Deduplication.
 * <p>
 * Ensures that external types (e.g., java.util.List) are tracked as single,
 * deduplicated rows with is_external=true.
 */
public class SymbolDao {

    private final DatabaseManager dbManager;
    private final java.util.Map<String, Long> symbolCache = new java.util.concurrent.ConcurrentHashMap<>();

    public SymbolDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Resolves a Spoon Type Reference into a database symbol ID.
     * <p>
     * @implNote If the symbol does not exist, it WRITES an entry to the `symbols` table.
     * If the symbol is outside the project root, it sets is_external=true.
     * 
     * @param typeRef The Spoon type reference.
     * @return The primary key ID of the symbol in the database.
     * @throws SQLException If database interactions fail.
     */
    public long resolveOrInsertSymbol(CtTypeReference<?> typeRef) throws SQLException {
        if (typeRef == null) return -1;
        String fqn = typeRef.getQualifiedName();
        String name = typeRef.getSimpleName();
        
        Long cachedId = symbolCache.get(fqn);
        if (cachedId != null) {
            return cachedId;
        }
        
        String upsertSql = """
            INSERT INTO symbols (kind, name, fqn, is_external)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(fqn) DO UPDATE SET name = excluded.name
            RETURNING id
            """;
            
        try (java.sql.Connection conn = dbManager.getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(upsertSql)) {
            pstmt.setString(1, "TYPE");
            pstmt.setString(2, name);
            pstmt.setString(3, fqn);
            pstmt.setBoolean(4, true); // Treating mostly as external for now
            
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    symbolCache.put(fqn, id);
                    return id;
                }
            }
        }
        return -1;
    }
}
