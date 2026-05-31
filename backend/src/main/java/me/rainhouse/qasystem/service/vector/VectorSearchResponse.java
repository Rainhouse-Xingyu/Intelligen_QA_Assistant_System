package me.rainhouse.qasystem.service.vector;

import java.util.List;

public record VectorSearchResponse(
        String query,
        String moduleType,
        Integer hitStatus,
        String hitLabel,
        double topScore,
        Long topKnowledgeId,
        String answer,
        long responseTimeMs,
        List<VectorSearchResult> results
) {
}
