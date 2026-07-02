package me.rainhouse.qasystem.service.vector;

import me.rainhouse.qasystem.entity.KbQaEntry;

public record VectorDocument(
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
        float[] vector
) {
    public static VectorDocument from(KbQaEntry entry, float[] vector) {
        return new VectorDocument(
                entry.getId(),
                entry.getQuestion(),
                entry.getAnswer(),
                entry.getModuleType(),
                entry.getCategoryL1Id(),
                entry.getCategoryL2Id(),
                entry.getCategoryL3Id(),
                categoryPath(entry),
                entry.getSourceType(),
                entry.getSourceUrl(),
                vector
        );
    }

    private static String categoryPath(KbQaEntry entry) {
        return java.util.stream.Stream.of(
                        entry.getCategoryL1Name(),
                        entry.getCategoryL2Name(),
                        entry.getCategoryL3Name()
                )
                .filter(org.springframework.util.StringUtils::hasText)
                .collect(java.util.stream.Collectors.joining(" > "));
    }
}
