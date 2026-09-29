package com.episteme.indexer;

import com.episteme.db.EdgeDao;
import spoon.reflect.reference.CtReference;
import spoon.reflect.declaration.CtElement;
import java.sql.SQLException;

/**
 * Orchestrates the creation of Edges between AST nodes.
 * <p>
 * Uses SymbolResolver to map Spoon elements to DB IDs, then writes the 
 * relationship to the DB via EdgeDao.
 */
public class EdgeBuilder {

    private final SymbolResolver resolver;
    private final EdgeDao edgeDao;

    public EdgeBuilder(SymbolResolver resolver, EdgeDao edgeDao) {
        this.resolver = resolver;
        this.edgeDao = edgeDao;
    }

    /**
     * Builds a typed edge (e.g. CALLS) from a source method to a target reference.
     * 
     * @param source The declaring context (e.g. the method making the call).
     * @param target The target reference (e.g. the method being called).
     * @param edgeType The type of edge ("CALLS", "READS", "WRITES", "EXTENDS").
     */
    public void buildEdge(CtElement source, CtReference target, String edgeType) {
        if (source == null || target == null) return;
        
        try {
            // Find the closest Reference for the source to get its FQN
            // A CtMethod is not a reference, so we get its reference
            CtReference sourceRef = null;
            if (source instanceof spoon.reflect.declaration.CtNamedElement) {
                sourceRef = ((spoon.reflect.declaration.CtNamedElement) source).getReference();
            } else if (source instanceof spoon.reflect.declaration.CtAnonymousExecutable) {
                spoon.reflect.declaration.CtType<?> parentType = source.getParent(spoon.reflect.declaration.CtType.class);
                if (parentType != null) {
                    sourceRef = parentType.getReference();
                }
            }
            
            if (sourceRef == null) return;
            
            long sourceId = resolver.resolveReference(sourceRef);
            long targetId = resolver.resolveReference(target);
            
            if (sourceId > 0 && targetId > 0) {
                edgeDao.addEdge(sourceId, targetId, edgeType);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Commits all pending edges to the database.
     */
    public void flush() throws SQLException {
        edgeDao.flush();
    }
}
