package me.rainhouse.qasystem.service;

import me.rainhouse.qasystem.entity.KbQaEntry;
import me.rainhouse.qasystem.service.vector.VectorSearchResponse;

import java.util.Collection;
import java.util.List;

public interface VectorSearchService {

    int rebuildIndex();

    void upsertEntry(KbQaEntry entry);

    void upsertEntries(Collection<KbQaEntry> entries);

    void removeEntry(Long knowledgeId);

    VectorSearchResponse search(String query, String moduleType, Integer topK, Long userId, Long sessionId);

    default VectorSearchResponse search(String query, List<String> moduleTypes, Integer topK, Long userId, Long sessionId) {
        String moduleType = moduleTypes == null || moduleTypes.isEmpty() ? null : moduleTypes.get(0);
        return search(query, moduleType, topK, userId, sessionId);
    }
}
