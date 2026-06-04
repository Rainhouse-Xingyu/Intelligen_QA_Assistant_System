package me.rainhouse.qasystem.service.impl;

import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.common.dto.AiChatResponse;
import me.rainhouse.qasystem.entity.QuestionRaw;
import me.rainhouse.qasystem.mapper.QuestionRawMapper;
import me.rainhouse.qasystem.service.AiChatService;
import me.rainhouse.qasystem.service.AnswerGeneratorService;
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

    public AiChatServiceImpl(QuestionRawMapper questionRawMapper,
                             QueryRewriteService queryRewriteService,
                             IntentClassifierService intentClassifierService,
                             VectorSearchService vectorSearchService,
                             AnswerGeneratorService answerGeneratorService,
                             CozeService cozeService,
                             UnrecognizedQueryService unrecognizedQueryService) {
        this.questionRawMapper = questionRawMapper;
        this.queryRewriteService = queryRewriteService;
        this.intentClassifierService = intentClassifierService;
        this.vectorSearchService = vectorSearchService;
        this.answerGeneratorService = answerGeneratorService;
        this.cozeService = cozeService;
        this.unrecognizedQueryService = unrecognizedQueryService;
    }

    @Override
    public AiChatResponse chat(Long userId, Long sessionId, String query, String selectedModuleType) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("提问内容不能为空");
        }

        long start = System.currentTimeMillis();
        String rewriteQuestion = queryRewriteService.rewrite(query);
        String moduleType = intentClassifierService.classify(rewriteQuestion, selectedModuleType);
        saveRawQuestion(userId, sessionId, query, moduleType);

        VectorSearchResponse searchResponse = vectorSearchService.search(rewriteQuestion, moduleType, 3, userId, sessionId);
        String answer = answerGeneratorService.generate(query, rewriteQuestion, searchResponse);
        String answerSource = "RAG";

        if (!StringUtils.hasText(answer)) {
            answer = firstKnowledgeAnswer(searchResponse);
            if (StringUtils.hasText(answer)) {
                answerSource = "RAG";
            } else {
                unrecognizedQueryService.recordUnrecognized(userId, query, moduleType, searchResponse.topScore());
                answer = cozeService.chat(String.valueOf(userId), query);
                answerSource = "Coze";
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
