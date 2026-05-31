package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.service.MilvusClientManager;
import me.rainhouse.qasystem.service.vector.VectorDocument;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MilvusClientManagerImpl implements MilvusClientManager {

    private final ConcurrentMap<Long, VectorDocument> index = new ConcurrentHashMap<>();

    @Override
    public void upsert(VectorDocument document) {
        if (document != null && document.knowledgeId() != null) {
            index.put(document.knowledgeId(), document);
        }
    }

    @Override
    public void upsertBatch(Collection<VectorDocument> documents) {
        if (documents == null) {
            return;
        }
        documents.forEach(this::upsert);
    }

    @Override
    public void remove(Long knowledgeId) {
        if (knowledgeId != null) {
            index.remove(knowledgeId);
        }
    }

    @Override
    public void clear() {
        index.clear();
    }

    @Override
    public int size() {
        return index.size();
    }

    @Override
    public List<VectorDocument> search(float[] queryVector, String moduleType, int topK) {
        return index.values().stream()
                .filter(document -> !StringUtils.hasText(moduleType) || moduleType.equals(document.moduleType()))
                .sorted(Comparator.comparingDouble((VectorDocument document) -> cosine(queryVector, document.vector())).reversed())
                .limit(Math.max(1, topK))
                .toList();
    }

    private double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length != right.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
        }
        return Math.max(0.0, Math.min(1.0, dot));
    }
}
