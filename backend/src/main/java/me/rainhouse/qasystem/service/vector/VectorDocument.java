package me.rainhouse.qasystem.service.vector;

import me.rainhouse.qasystem.entity.KbQaEntry;

public record VectorDocument(
        Long knowledgeId,
        String question,
        String answer,
        String moduleType,
        String sourceType,
        float[] vector
) {
    public static VectorDocument from(KbQaEntry entry, float[] vector) {
        return new VectorDocument(
                entry.getId(),
                entry.getQuestion(),
                entry.getAnswer(),
                entry.getModuleType(),
                entry.getSourceType(),
                vector
        );
    }
}
