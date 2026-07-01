package me.rainhouse.qasystem.service.impl;

import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.service.AnswerGeneratorService;
import me.rainhouse.qasystem.service.ChatMemoryService;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
import me.rainhouse.qasystem.service.vector.VectorSearchResponse;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class AnswerGeneratorServiceImpl implements AnswerGeneratorService {

    private final LocalModelClient localModelClient;
    private final ChatMemoryService chatMemoryService;

    public AnswerGeneratorServiceImpl(AiModelProperties aiModelProperties,
                                      LocalModelClient localModelClient,
                                      ChatMemoryService chatMemoryService) {
        this.localModelClient = localModelClient;
        this.chatMemoryService = chatMemoryService;
        aiModelProperties.getQwenGeneratorPath();
    }

    @Override
    public String generate(String originalQuestion, String rewriteQuestion, VectorSearchResponse searchResponse) {
        return generate(originalQuestion, rewriteQuestion, searchResponse, "");
    }

    @Override
    public String generate(String originalQuestion,
                           String rewriteQuestion,
                           VectorSearchResponse searchResponse,
                           String memoryContext) {
        if (searchResponse == null || searchResponse.results() == null || searchResponse.results().isEmpty()) {
            log.info("[AI] /generate skipped: no vector search result");
            return null;
        }
        if (searchResponse.hitStatus() == null || searchResponse.hitStatus() == 0) {
            log.info("[AI] /generate skipped: hitStatus={}, topScore={}",
                    searchResponse.hitStatus(), searchResponse.topScore());
            return null;
        }

        if (localModelClient.enabled()) {
            log.info("[AI] /generate call local model: hitStatus={}, topScore={}, references={}",
                    searchResponse.hitStatus(), searchResponse.topScore(), searchResponse.results().size());
            String answer = localModelClient.generate(
                    chatMemoryService.buildGenerationQuestion(originalQuestion, memoryContext),
                    rewriteQuestion,
                    searchResponse.results());
            if (StringUtils.hasText(answer)) {
                return answer;
            }
            log.info("[AI] /generate local model returned empty text, fallback to top knowledge answer");
        }

        VectorSearchResult top = searchResponse.results().get(0);
        if (!StringUtils.hasText(top.answer())) {
            return null;
        }
        StringBuilder answer = new StringBuilder();
        answer.append(top.answer().trim());

        List<VectorSearchResult> references = searchResponse.results();
        if (!references.isEmpty()) {
            answer.append("\n\nReference sources:");
            for (int i = 0; i < Math.min(3, references.size()); i++) {
                VectorSearchResult result = references.get(i);
                answer.append("\n").append(i + 1)
                        .append(". Knowledge ").append(result.knowledgeId())
                        .append(" \"").append(result.question()).append("\"")
                        .append(", score ").append(String.format("%.4f", result.finalScore()));
            }
        }
        return answer.toString();
    }
}
