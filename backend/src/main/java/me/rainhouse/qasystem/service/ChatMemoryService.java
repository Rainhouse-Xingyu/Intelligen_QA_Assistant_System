package me.rainhouse.qasystem.service;

public interface ChatMemoryService {

    String buildRecentContext(Long sessionId, String currentQuery);

    String buildRetrievalQuery(String query, String memoryContext);

    String buildGenerationQuestion(String originalQuestion, String memoryContext);
}
