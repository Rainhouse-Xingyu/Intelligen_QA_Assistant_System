package me.rainhouse.qasystem.service.localmodel;

import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import me.rainhouse.qasystem.service.kb.FaqItem;

import java.util.List;

public interface LocalModelClient {

    float[] embed(String text);

    List<Double> rerank(String query, List<String> documents);

    String rewrite(String query);

    String classify(String query, List<String> candidateModules);

    String generate(String originalQuestion, String rewriteQuestion, List<VectorSearchResult> references);

    default List<FaqItem> chunkToFaq(String text, String title, int maxItems) {
        return List.of();
    }

    default List<FaqItem> chunkToFaq(String text, String title, int maxItems, List<String> categoryPaths) {
        return chunkToFaq(text, title, maxItems);
    }

    boolean enabled();
}
