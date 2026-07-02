package me.rainhouse.qasystem.service.impl;

import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.common.dto.AiChatResponse;
import me.rainhouse.qasystem.common.utils.CasualConversationUtils;
import me.rainhouse.qasystem.entity.QuestionRaw;
import me.rainhouse.qasystem.mapper.QuestionRawMapper;
import me.rainhouse.qasystem.service.AiChatService;
import me.rainhouse.qasystem.service.AnswerGeneratorService;
import me.rainhouse.qasystem.service.ChatMemoryService;
import me.rainhouse.qasystem.service.CozeService;
import me.rainhouse.qasystem.service.IntentClassifierService;
import me.rainhouse.qasystem.service.QueryRewriteService;
import me.rainhouse.qasystem.service.UnrecognizedQueryService;
import me.rainhouse.qasystem.service.VectorSearchService;
import me.rainhouse.qasystem.service.vector.VectorSearchResponse;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    private final QuestionRawMapper questionRawMapper;
    private final QueryRewriteService queryRewriteService;
    private final IntentClassifierService intentClassifierService;
    private final VectorSearchService vectorSearchService;
    private final AnswerGeneratorService answerGeneratorService;
    private final CozeService cozeService;
    private final UnrecognizedQueryService unrecognizedQueryService;
    private final ChatMemoryService chatMemoryService;

    public AiChatServiceImpl(QuestionRawMapper questionRawMapper,
                             QueryRewriteService queryRewriteService,
                             IntentClassifierService intentClassifierService,
                             VectorSearchService vectorSearchService,
                             AnswerGeneratorService answerGeneratorService,
                             CozeService cozeService,
                             UnrecognizedQueryService unrecognizedQueryService,
                             ChatMemoryService chatMemoryService) {
        this.questionRawMapper = questionRawMapper;
        this.queryRewriteService = queryRewriteService;
        this.intentClassifierService = intentClassifierService;
        this.vectorSearchService = vectorSearchService;
        this.answerGeneratorService = answerGeneratorService;
        this.cozeService = cozeService;
        this.unrecognizedQueryService = unrecognizedQueryService;
        this.chatMemoryService = chatMemoryService;
    }

    @Override
    public AiChatResponse chat(Long userId, Long sessionId, String query, String selectedModuleType) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("Question cannot be empty");
        }

        long start = System.currentTimeMillis();
        if (CasualConversationUtils.isCasualOnly(query)) {
            return AiChatResponse.builder()
                    .originalQuestion(query)
                    .rewriteQuestion(query.trim())
                    .moduleType("闲聊")
                    .hitStatus(0)
                    .hitLabel("直接回复")
                    .topScore(0.0)
                    .answer(CasualConversationUtils.directReply(query))
                    .answerSource("DIRECT_REPLY")
                    .responseTimeMs(System.currentTimeMillis() - start)
                    .references(List.of())
                    .build();
        }

        String memoryContext = chatMemoryService.buildRecentContext(sessionId, query);
        String rewriteQuestion = queryRewriteService.rewrite(query);
        String retrievalQuestion = chatMemoryService.buildRetrievalQuery(rewriteQuestion, memoryContext);
        String moduleType = intentClassifierService.classify(retrievalQuestion, selectedModuleType);
        saveRawQuestion(userId, sessionId, query, moduleType);

        VectorSearchResponse searchResponse = vectorSearchService.search(retrievalQuestion, moduleType, 3, userId, sessionId);
        String answer = answerGeneratorService.generate(query, rewriteQuestion, searchResponse, memoryContext);
        String answerSource = "RAG";

        if (isEchoAnswer(query, answer)) {
            log.warn("[AI] generated answer echoed the question, fallback to knowledge answer. sessionId={}", sessionId);
            answer = firstKnowledgeAnswer(searchResponse);
        }

        if (!StringUtils.hasText(answer)) {
            answer = firstKnowledgeAnswer(searchResponse);
            if (StringUtils.hasText(answer)) {
                answerSource = "RAG";
            } else {
                unrecognizedQueryService.recordUnrecognized(userId, query, moduleType, searchResponse.topScore());
            }
        }

        return AiChatResponse.builder()
                .originalQuestion(query)
                .rewriteQuestion(rewriteQuestion)
                .moduleType(moduleType)
                .hitStatus(searchResponse.hitStatus())
                .hitLabel(searchResponse.hitLabel())
                .topScore(searchResponse.topScore())
                .topKnowledgeId(searchResponse.topKnowledgeId())
                .answer(answer)
                .answerSource(answerSource)
                .responseTimeMs(System.currentTimeMillis() - start)
                .references(searchResponse.results())
                .build();
    }

    private String firstKnowledgeAnswer(VectorSearchResponse searchResponse) {
        if (searchResponse == null || searchResponse.results() == null) {
            return null;
        }
        return searchResponse.results().stream()
                .map(VectorSearchResult::answer)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private boolean isEchoAnswer(String query, String answer) {
        String normalizedQuery = normalizeForCompare(query);
        String normalizedAnswer = normalizeForCompare(answer);
        if (!StringUtils.hasText(normalizedQuery) || !StringUtils.hasText(normalizedAnswer)) {
            return false;
        }
        return normalizedAnswer.equals(normalizedQuery)
                || (normalizedAnswer.length() <= normalizedQuery.length() + 4
                && normalizedAnswer.contains(normalizedQuery));
    }

    private String normalizeForCompare(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private void saveRawQuestion(Long userId, Long sessionId, String originalQuestion, String moduleType) {
        QuestionRaw raw = new QuestionRaw();
        raw.setUserId(userId);
        raw.setSessionId(sessionId);
        raw.setOriginalQuestion(originalQuestion);
        raw.setModuleType(moduleType);
        raw.setCreatedAt(LocalDateTime.now());
        questionRawMapper.insert(raw);
    }
}
