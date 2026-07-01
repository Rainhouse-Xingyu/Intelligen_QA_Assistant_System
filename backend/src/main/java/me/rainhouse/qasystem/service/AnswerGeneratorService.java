package me.rainhouse.qasystem.service;

import me.rainhouse.qasystem.service.vector.VectorSearchResponse;

public interface AnswerGeneratorService {

    String generate(String originalQuestion, String rewriteQuestion, VectorSearchResponse searchResponse);

    String generate(String originalQuestion, String rewriteQuestion, VectorSearchResponse searchResponse, String memoryContext);
}
