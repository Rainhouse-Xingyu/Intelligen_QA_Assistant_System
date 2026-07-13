package me.rainhouse.qasystem.service.impl;

import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.common.dto.AiChatResponse;
import me.rainhouse.qasystem.common.utils.AnswerTextSanitizer;
import me.rainhouse.qasystem.common.utils.CasualConversationUtils;
import me.rainhouse.qasystem.entity.QuestionRaw;
import me.rainhouse.qasystem.mapper.QuestionRawMapper;
import me.rainhouse.qasystem.service.AiChatService;
import me.rainhouse.qasystem.service.AnswerGeneratorService;
import me.rainhouse.qasystem.service.ChatMemoryService;
import me.rainhouse.qasystem.service.IntentClassifierService;
import me.rainhouse.qasystem.service.QueryRewriteService;
import me.rainhouse.qasystem.service.UnrecognizedQueryService;
import me.rainhouse.qasystem.service.VectorSearchService;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
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

    private static final String AI_INTERPRETATION_NOTICE =
            "以上答复含有AI解读成分，如有未尽事宜或其他问题，请具体咨询教务部联系人https://jw.neusoft.edu.cn/25565/";

    private final QuestionRawMapper questionRawMapper;
    private final QueryRewriteService queryRewriteService;
    private final IntentClassifierService intentClassifierService;
    private final VectorSearchService vectorSearchService;
    private final AnswerGeneratorService answerGeneratorService;
    private final LocalModelClient localModelClient;
    private final UnrecognizedQueryService unrecognizedQueryService;
    private final ChatMemoryService chatMemoryService;

    public AiChatServiceImpl(QuestionRawMapper questionRawMapper,
                             QueryRewriteService queryRewriteService,
                             IntentClassifierService intentClassifierService,
                             VectorSearchService vectorSearchService,
                             AnswerGeneratorService answerGeneratorService,
                             LocalModelClient localModelClient,
                             UnrecognizedQueryService unrecognizedQueryService,
                             ChatMemoryService chatMemoryService) {
        this.questionRawMapper = questionRawMapper;
        this.queryRewriteService = queryRewriteService;
        this.intentClassifierService = intentClassifierService;
        this.vectorSearchService = vectorSearchService;
        this.answerGeneratorService = answerGeneratorService;
        this.localModelClient = localModelClient;
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
        List<String> moduleCandidates = shouldRouteToPsychology(query, selectedModuleType)
                ? List.of("心理辅导")
                : intentClassifierService.classifyCandidates(rewriteQuestion, selectedModuleType);
        String moduleType = moduleCandidates.stream().findFirst().orElse(null);
        saveRawQuestion(userId, sessionId, query, moduleType);

        if ("心理辅导".equals(moduleType)) {
            String answer = generatePsychologicalAnswer(query);
            return AiChatResponse.builder()
                    .originalQuestion(query)
                    .rewriteQuestion(rewriteQuestion)
                    .moduleType(moduleType)
                    .hitStatus(1)
                    .hitLabel("心理指导")
                    .topScore(1.0)
                    .answer(answer)
                    .answerSource("LOCAL_PSY")
                    .responseTimeMs(System.currentTimeMillis() - start)
                    .references(List.of())
                    .build();
        }

        VectorSearchResponse searchResponse = moduleCandidates.size() <= 1
                ? vectorSearchService.search(retrievalQuestion, moduleType, 3, userId, sessionId)
                : vectorSearchService.search(retrievalQuestion, moduleCandidates, 3, userId, sessionId);
        if (StringUtils.hasText(searchResponse.moduleType())) {
            moduleType = searchResponse.moduleType();
        }
        String answer = answerGeneratorService.generate(query, rewriteQuestion, searchResponse, memoryContext);
        answer = AnswerTextSanitizer.stripReferenceSection(answer);
        String answerSource = "RAG";

        if (isEchoAnswer(query, answer)) {
            log.warn("[AI] generated answer echoed the question, fallback to knowledge answer. sessionId={}", sessionId);
            answer = hitKnowledgeAnswer(searchResponse);
        }

        if (!StringUtils.hasText(answer)) {
            answer = hitKnowledgeAnswer(searchResponse);
            if (StringUtils.hasText(answer)) {
                answerSource = "RAG";
            } else {
                unrecognizedQueryService.recordUnrecognized(userId, query, moduleType, searchResponse.topScore());
                answer = "暂时没有找到和这个问题匹配的答案，我已经记录下来，后续会继续完善知识库。你也可以换一种说法再问我一次。";
                answerSource = "NO_ANSWER_FALLBACK";
            }
        }

        answer = AnswerTextSanitizer.stripReferenceSection(answer);

        return AiChatResponse.builder()
                .originalQuestion(query)
                .rewriteQuestion(rewriteQuestion)
                .moduleType(moduleType)
                .hitStatus(searchResponse.hitStatus())
                .hitLabel(searchResponse.hitLabel())
                .topScore(searchResponse.topScore())
                .topKnowledgeId(searchResponse.topKnowledgeId())
                .answer(formatBusinessAnswer(answer, moduleType, query))
                .answerSource(answerSource)
                .responseTimeMs(System.currentTimeMillis() - start)
                .references(searchResponse.results())
                .build();
    }

    private String hitKnowledgeAnswer(VectorSearchResponse searchResponse) {
        if (searchResponse == null || searchResponse.results() == null) {
            return null;
        }
        if (searchResponse.hitStatus() == null || searchResponse.hitStatus() == 0) {
            return null;
        }
        return searchResponse.results().stream()
                .map(VectorSearchResult::answer)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String generatePsychologicalAnswer(String query) {
        try {
            String answer = localModelClient.psychologicalCounseling(query);
            if (StringUtils.hasText(answer)) {
                return answer;
            }
        } catch (Exception ex) {
            log.warn("[AI] local psychological counseling failed, fallback to canned reply: {}", ex.getMessage());
        }
        return LocalModelClient.superFallbackPsychologicalCounseling(query);
    }

    private boolean shouldRouteToPsychology(String query, String selectedModuleType) {
        if (StringUtils.hasText(selectedModuleType)) {
            return false;
        }
        if (!StringUtils.hasText(query)) {
            return false;
        }
        String text = normalizeForCompare(query);
        return text.contains("心理")
                || text.contains("焦虑")
                || text.contains("压力大")
                || text.contains("紧张")
                || text.contains("烦躁")
                || text.contains("难过")
                || text.contains("崩溃")
                || text.contains("失眠")
                || text.contains("睡不着")
                || text.contains("抑郁")
                || text.contains("不开心")
                || text.contains("想哭")
                || text.contains("害怕")
                || text.contains("孤独")
                || text.contains("迷茫");
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

    private String formatBusinessAnswer(String answer, String moduleType, String query) {
        if (!StringUtils.hasText(answer)) {
            return answer;
        }
        String normalizedAnswer = ensureTerminalPunctuation(answer.trim());
        String closing = isExamQuestion(moduleType, query)
                ? "祝您考试取得好成绩！"
                : "谢谢！";
        return "同学，你好！\n"
                + "您所咨询的问题解答如下：" + normalizedAnswer + closing
                + "\n\n"
                + AI_INTERPRETATION_NOTICE;
    }

    private String ensureTerminalPunctuation(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.matches(".*[。！？!?；;.]$")
                ? text
                : text + "。";
    }

    private boolean isExamQuestion(String moduleType, String query) {
        String text = ((moduleType == null ? "" : moduleType) + " " + (query == null ? "" : query)).toLowerCase(Locale.ROOT);
        return text.contains("考务")
                || text.contains("考试")
                || text.contains("四六级")
                || text.contains("四级")
                || text.contains("六级")
                || text.contains("补考")
                || text.contains("缓考")
                || text.contains("成绩");
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
