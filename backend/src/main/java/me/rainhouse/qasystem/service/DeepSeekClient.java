package me.rainhouse.qasystem.service;

import me.rainhouse.qasystem.service.vector.VectorSearchResult;

import java.util.List;

public interface DeepSeekClient {
    boolean enabled();

    String generate(String originalQuestion,
                    String rewriteQuestion,
                    String memoryContext,
                    List<VectorSearchResult> references);
}
