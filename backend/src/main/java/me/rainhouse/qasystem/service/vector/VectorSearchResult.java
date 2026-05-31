package me.rainhouse.qasystem.service.vector;

public record VectorSearchResult(
        Long knowledgeId,
        String question,
        String answer,
        String moduleType,
        String sourceType,
        double vectorScore,
        double rerankScore,
        double finalScore
) {
}
