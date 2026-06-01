package me.rainhouse.qasystem.service.localmodel;

import me.rainhouse.qasystem.service.vector.VectorSearchResult;

import java.util.List;

public interface LocalModelClient {

    float[] embed(String text);

    List<Double> rerank(String query, List<String> documents);

    String rewrite(String query);

    String classify(String query, List<String> candidateModules);

    String generate(String originalQuestion, String rewriteQuestion, List<VectorSearchResult> references);

    boolean enabled();
}
