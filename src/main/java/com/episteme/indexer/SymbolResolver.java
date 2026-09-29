package com.episteme.indexer;

import spoon.reflect.reference.CtReference;
import com.episteme.db.DatabaseManager;

/**
 * Resolves Spoon AST References across different files.
 * <p>
 * Maps Spoon's internal model references (CtReference) to the physical
 * database `symbols` table by computing and querying the Fully Qualified Name (FQN).
 */
public class SymbolResolver {

    private final DatabaseManager dbManager;
    private final java.util.Map<String, Long> symbolCache = new java.util.concurrent.ConcurrentHashMap<>();

    public SymbolResolver(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Extracts a globally unique signature (FQN) from a Spoon reference.
     */
    private String extractFqn(CtReference ref) {
        if (ref instanceof spoon.reflect.reference.CtTypeReference) {
            spoon.reflect.reference.CtTypeReference<?> typeRef = (spoon.reflect.reference.CtTypeReference<?>) ref;
            // Get raw type to avoid generic clutter (e.g., List<String> -> List)
            return typeRef.getTypeErasure().getQualifiedName();
        } else if (ref instanceof spoon.reflect.reference.CtExecutableReference) {
            spoon.reflect.reference.CtExecutableReference<?> execRef = (spoon.reflect.reference.CtExecutableReference<?>) ref;
            String declaringType = execRef.getDeclaringType() != null ? execRef.getDeclaringType().getTypeErasure().getQualifiedName() : "UnknownType";
            return declaringType + "#" + execRef.getSignature();
        } else if (ref instanceof spoon.reflect.reference.CtFieldReference) {
            spoon.reflect.reference.CtFieldReference<?> fieldRef = (spoon.reflect.reference.CtFieldReference<?>) ref;
            String declaringType = fieldRef.getDeclaringType() != null ? fieldRef.getDeclaringType().getTypeErasure().getQualifiedName() : "UnknownType";
            return declaringType + "#" + fieldRef.getSimpleName();
        }
        return ref.getSimpleName();
    }

    /**
     * Determines if a reference is external to the source code (e.g., standard library).
     */
    private boolean isExternal(CtReference ref) {
        // If the declaration cannot be found in the current Spoon model, it's external.
        return ref.getDeclaration() == null;
    }

    /**
     * Resolves a Spoon reference to its corresponding `symbol_id` in the database.
     * <p>
     * @implNote 
     * 1. Evaluates the FQN of the reference.
     * 2. Queries the `symbols` table. If found, returns the ID.
     * 3. If NOT found, and the symbol is external (e.g. java.util.*),
     *    inserts a stub row with `is_external = true`.
     * 
     * @param ref The Spoon AST reference (can be a type, method, or field reference).
     * @return The primary key (ID) of the symbol in the database, or -1 if unresolved.
     */
    public long resolveReference(CtReference ref) {
        if (ref == null) return -1L;
        
        // Skip Primitives and Void to prevent Knowledge Graph noise
        if (ref instanceof spoon.reflect.reference.CtTypeReference) {
            spoon.reflect.reference.CtTypeReference<?> typeRef = (spoon.reflect.reference.CtTypeReference<?>) ref;
            if (typeRef.isPrimitive() || "void".equals(typeRef.getSimpleName())) {
                return -1L; 
            }
        }
        
        String fqn = extractFqn(ref);
        boolean external = isExternal(ref);
        String name = ref.getSimpleName();
        String kind = ref.getClass().getSimpleName().replace("ReferenceImpl", "").toUpperCase();
        
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
            pstmt.setString(1, kind);
            pstmt.setString(2, name);
            pstmt.setString(3, fqn);
            pstmt.setBoolean(4, external);
            
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    symbolCache.put(fqn, id);
                    return id;
                }
            }
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
        
        return -1L;
    }
}
