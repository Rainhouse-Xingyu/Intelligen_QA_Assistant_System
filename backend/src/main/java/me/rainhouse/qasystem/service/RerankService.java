package me.rainhouse.qasystem.service;

import me.rainhouse.qasystem.service.vector.VectorDocument;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;

import java.util.List;

public interface RerankService {

    List<VectorSearchResult> rerank(String query, float[] queryVector, List<VectorDocument> candidates, int topK);
}
