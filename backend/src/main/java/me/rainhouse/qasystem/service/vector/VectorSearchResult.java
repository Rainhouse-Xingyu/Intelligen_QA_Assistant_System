package me.rainhouse.qasystem.service.vector;

public record VectorSearchResult(
        Long knowledgeId,
        String question,
        String answer,
        String moduleType,
        Long categoryL1Id,
        Long categoryL2Id,
        Long categoryL3Id,
        String categoryPath,
        String sourceType,
        String sourceUrl,
        double vectorScore,
        double rerankScore,
        double finalScore
) {
}
