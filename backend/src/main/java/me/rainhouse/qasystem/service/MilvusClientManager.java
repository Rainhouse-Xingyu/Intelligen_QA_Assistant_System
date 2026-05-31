package me.rainhouse.qasystem.service;

import me.rainhouse.qasystem.service.vector.VectorDocument;

import java.util.Collection;
import java.util.List;

public interface MilvusClientManager {

    void upsert(VectorDocument document);

    void upsertBatch(Collection<VectorDocument> documents);

    void remove(Long knowledgeId);

    void clear();

    int size();

    List<VectorDocument> search(float[] queryVector, String moduleType, int topK);
}
