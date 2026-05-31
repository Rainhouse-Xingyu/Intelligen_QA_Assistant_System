package me.rainhouse.qasystem.service;

import me.rainhouse.qasystem.entity.KbQaEntry;
import me.rainhouse.qasystem.service.vector.VectorSearchResponse;

import java.util.Collection;

public interface VectorSearchService {

    int rebuildIndex();

    void upsertEntry(KbQaEntry entry);

    void upsertEntries(Collection<KbQaEntry> entries);

    void removeEntry(Long knowledgeId);

    VectorSearchResponse search(String query, String moduleType, Integer topK, Long userId, Long sessionId);
}
