package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.common.dto.AiChatResponse;
import me.rainhouse.qasystem.entity.QuestionRaw;
import me.rainhouse.qasystem.mapper.QuestionRawMapper;
import me.rainhouse.qasystem.service.AnswerGeneratorService;
import me.rainhouse.qasystem.service.ChatMemoryService;
import me.rainhouse.qasystem.service.IntentClassifierService;
import me.rainhouse.qasystem.service.QueryRewriteService;
import me.rainhouse.qasystem.service.UnrecognizedQueryService;
import me.rainhouse.qasystem.service.VectorSearchService;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
import me.rainhouse.qasystem.service.vector.VectorSearchResponse;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceImplTest {

    @Test
    void unmatchedSearchDoesNotFallbackToTopKnowledgeAnswer() {
        QuestionRawMapper questionRawMapper = mock(QuestionRawMapper.class);
        QueryRewriteService rewriteService = query -> query + "？";
        IntentClassifierService classifierService = (query, selectedModule) -> "考务通知";
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        AnswerGeneratorService answerGeneratorService = mock(AnswerGeneratorService.class);
        UnrecognizedQueryService unrecognizedQueryService = mock(UnrecognizedQueryService.class);
        ChatMemoryService chatMemoryService = noMemory();
        when(questionRawMapper.insert(any(QuestionRaw.class))).thenReturn(1);
        when(vectorSearchService.search(any(), any(String.class), any(), any(), any())).thenReturn(searchResponse(0, 0.31));
        when(answerGeneratorService.generate(any(), any(), any(), any())).thenReturn(null);

        AiChatServiceImpl service = new AiChatServiceImpl(
                questionRawMapper,
                rewriteService,
                classifierService,
                vectorSearchService,
                answerGeneratorService,
                fixedPsychModel(),
                unrecognizedQueryService,
                chatMemoryService
        );

        AiChatResponse response = service.chat(1L, 1L, "随便问一个知识库没有的问题", null);

        assertEquals("NO_ANSWER_FALLBACK", response.getAnswerSource());
        assertFalse(response.getAnswer().contains("线上监考"));
        verify(unrecognizedQueryService).recordUnrecognized(1L, "随便问一个知识库没有的问题", "考务通知", 0.31);
    }

    @Test
    void hitSearchCanFallbackToTopKnowledgeAnswerWhenGeneratorReturnsEmpty() {
        QuestionRawMapper questionRawMapper = mock(QuestionRawMapper.class);
        QueryRewriteService rewriteService = query -> query + "？";
        IntentClassifierService classifierService = (query, selectedModule) -> "考务通知";
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        AnswerGeneratorService answerGeneratorService = mock(AnswerGeneratorService.class);
        ChatMemoryService chatMemoryService = noMemory();
        when(questionRawMapper.insert(any(QuestionRaw.class))).thenReturn(1);
        when(vectorSearchService.search(any(), any(String.class), any(), any(), any())).thenReturn(searchResponse(1, 0.62));
        when(answerGeneratorService.generate(any(), any(), any(), any())).thenReturn(null);

        AiChatServiceImpl service = new AiChatServiceImpl(
                questionRawMapper,
                rewriteService,
                classifierService,
                vectorSearchService,
                answerGeneratorService,
                fixedPsychModel(),
                mock(UnrecognizedQueryService.class),
                chatMemoryService
        );

        AiChatResponse response = service.chat(1L, 1L, "线上考试要求", null);

        assertEquals("RAG", response.getAnswerSource());
        assertEquals("""
                同学，你好！
                您所咨询的问题解答如下：线上监考文章内容。祝您考试取得好成绩！

                以上答复含有AI解读成分，如有未尽事宜或其他问题，请具体咨询教务部联系人https://jw.neusoft.edu.cn/25565/""", response.getAnswer());
    }

    @Test
    void nonExamBusinessAnswerUsesThanksClosing() {
        QuestionRawMapper questionRawMapper = mock(QuestionRawMapper.class);
        QueryRewriteService rewriteService = query -> query + "？";
        IntentClassifierService classifierService = (query, selectedModule) -> "教学运行";
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        AnswerGeneratorService answerGeneratorService = mock(AnswerGeneratorService.class);
        ChatMemoryService chatMemoryService = noMemory();
        when(questionRawMapper.insert(any(QuestionRaw.class))).thenReturn(1);
        when(vectorSearchService.search(any(), any(String.class), any(), any(), any()))
                .thenReturn(searchResponse(1, 0.72, "教学运行"));
        when(answerGeneratorService.generate(any(), any(), any(), any())).thenReturn("选课安排以教务部通知为准");

        AiChatServiceImpl service = new AiChatServiceImpl(
                questionRawMapper,
                rewriteService,
                classifierService,
                vectorSearchService,
                answerGeneratorService,
                fixedPsychModel(),
                mock(UnrecognizedQueryService.class),
                chatMemoryService
        );

        AiChatResponse response = service.chat(1L, 1L, "什么时候选课", null);

        assertEquals("""
                同学，你好！
                您所咨询的问题解答如下：选课安排以教务部通知为准。谢谢！

                以上答复含有AI解读成分，如有未尽事宜或其他问题，请具体咨询教务部联系人https://jw.neusoft.edu.cn/25565/""", response.getAnswer());
    }

    @Test
    void psychologicalModuleUsesLocalModelWithoutPolicyTemplate() {
        QuestionRawMapper questionRawMapper = mock(QuestionRawMapper.class);
        QueryRewriteService rewriteService = query -> query;
        IntentClassifierService classifierService = (query, selectedModule) -> "心理辅导";
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        AnswerGeneratorService answerGeneratorService = mock(AnswerGeneratorService.class);
        ChatMemoryService chatMemoryService = noMemory();
        when(questionRawMapper.insert(any(QuestionRaw.class))).thenReturn(1);

        AiChatServiceImpl service = new AiChatServiceImpl(
                questionRawMapper,
                rewriteService,
                classifierService,
                vectorSearchService,
                answerGeneratorService,
                fixedPsychModel(),
                mock(UnrecognizedQueryService.class),
                chatMemoryService
        );

        AiChatResponse response = service.chat(1L, 1L, "最近有点焦虑", null);

        assertEquals("LOCAL_PSY", response.getAnswerSource());
        assertEquals("别急，我们先把事情拆小一点。", response.getAnswer());
        assertFalse(response.getAnswer().contains("您所咨询的问题解答如下"));
    }

    @Test
    void psychologicalKeywordsRouteToLocalModelWithoutSelectedCategory() {
        QuestionRawMapper questionRawMapper = mock(QuestionRawMapper.class);
        QueryRewriteService rewriteService = query -> query;
        IntentClassifierService classifierService = mock(IntentClassifierService.class);
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        AnswerGeneratorService answerGeneratorService = mock(AnswerGeneratorService.class);
        ChatMemoryService chatMemoryService = noMemory();
        when(questionRawMapper.insert(any(QuestionRaw.class))).thenReturn(1);

        AiChatServiceImpl service = new AiChatServiceImpl(
                questionRawMapper,
                rewriteService,
                classifierService,
                vectorSearchService,
                answerGeneratorService,
                fixedPsychModel(),
                mock(UnrecognizedQueryService.class),
                chatMemoryService
        );

        AiChatResponse response = service.chat(1L, 1L, "我最近压力大，晚上总是睡不着", null);

        assertEquals("心理辅导", response.getModuleType());
        assertEquals("LOCAL_PSY", response.getAnswerSource());
        verify(classifierService, never()).classifyCandidates(any(), any());
        verify(vectorSearchService, never()).search(any(), any(String.class), any(), any(), any());
    }

    private static ChatMemoryService noMemory() {
        return new ChatMemoryService() {
            @Override
            public String buildRecentContext(Long sessionId, String currentQuery) {
                return "";
            }

            @Override
            public String buildRetrievalQuery(String query, String memoryContext) {
                return query;
            }

            @Override
            public String buildGenerationQuestion(String originalQuestion, String memoryContext) {
                return originalQuestion;
            }
        };
    }

    private static LocalModelClient fixedPsychModel() {
        return new LocalModelClient() {
            @Override
            public float[] embed(String text) {
                return new float[0];
            }

            @Override
            public List<Double> rerank(String query, List<String> documents) {
                return List.of();
            }

            @Override
            public String rewrite(String query) {
                return query;
            }

            @Override
            public String classify(String query, List<String> candidateModules) {
                return "";
            }

            @Override
            public String generate(String originalQuestion, String rewriteQuestion, List<VectorSearchResult> references) {
                return "";
            }

            @Override
            public String psychologicalCounseling(String studentMsg) {
                return "别急，我们先把事情拆小一点。";
            }

            @Override
            public boolean enabled() {
                return true;
            }
        };
    }

    private static VectorSearchResponse searchResponse(Integer hitStatus, double topScore) {
        return searchResponse(hitStatus, topScore, "考务通知");
    }

    private static VectorSearchResponse searchResponse(Integer hitStatus, double topScore, String moduleType) {
        VectorSearchResult result = new VectorSearchResult(
                100L,
                "线上监考",
                "线上监考文章内容",
                moduleType,
                null,
                null,
                null,
                "考务通知 > 考试",
                null,
                null,
                topScore,
                topScore,
                topScore
        );
        return new VectorSearchResponse(
                "query",
                moduleType,
                hitStatus,
                hitStatus == null || hitStatus == 0 ? "未命中" : "弱命中",
                topScore,
                100L,
                hitStatus == null || hitStatus == 0 ? null : result.answer(),
                12L,
                List.of(result)
        );
    }
}
