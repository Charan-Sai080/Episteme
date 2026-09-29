package com.episteme.dto;

/**
 * Data Transfer Object representing a traversed node in the Knowledge Graph.
 * Encapsulates symbol information, the depth at which it was found, and the edge type.
 */
public record GraphNodeDTO(
        long symbolId,
        String name,
        String fqn,
        String kind,
        int depth,
        String edgeType
) {}
