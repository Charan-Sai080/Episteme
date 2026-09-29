package com.episteme.dto.export;

import java.util.List;

public record GraphExportDTO(List<ExportNodeDTO> nodes, List<ExportEdgeDTO> edges) {
    public GraphExportDTO {
        nodes = (nodes == null) ? java.util.List.of() : java.util.List.copyOf(nodes);
        edges = (edges == null) ? java.util.List.of() : java.util.List.copyOf(edges);
    }
}
