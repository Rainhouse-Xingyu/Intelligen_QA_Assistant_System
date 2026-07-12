package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.entity.QuestionHitRecord;
import me.rainhouse.qasystem.mapper.QuestionHitRecordMapper;
import me.rainhouse.qasystem.service.EmbeddingService;
import me.rainhouse.qasystem.service.HitRuleEngine;
import me.rainhouse.qasystem.service.KbQaEntryService;
import me.rainhouse.qasystem.service.MilvusClientManager;
import me.rainhouse.qasystem.service.RerankService;
import me.rainhouse.qasystem.service.vector.HitDecision;
import me.rainhouse.qasystem.service.vector.VectorDocument;
import me.rainhouse.qasystem.service.vector.VectorSearchResponse;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorSearchServiceImplTest {

    @Test
    void moduleAliasFindsExamVectorsForExamNoticeModule() {
        MilvusClientManager milvus = mock(MilvusClientManager.class);
        QuestionHitRecordMapper hitRecordMapper = mock(QuestionHitRecordMapper.class);
        when(milvus.size()).thenReturn(10);
        when(milvus.search(any(), eq("考试"), anyInt())).thenReturn(List.of(document(1L, "考试")));
        when(hitRecordMapper.insert(any(QuestionHitRecord.class))).thenReturn(1);

        VectorSearchServiceImpl service = service(milvus, hitRecordMapper);

        VectorSearchResponse response = service.search("四级考试报名时间是什么时候？", "考务通知", 3, 1L, 1L);

        assertEquals(0.82, response.topScore());
        assertEquals(1L, response.topKnowledgeId());
        verify(milvus).search(any(), eq("考务通知"), anyInt());
        verify(milvus).search(any(), eq("考试"), anyInt());
    }

    @Test
    void moduleFilterMissFallsBackToAllVectors() {
        MilvusClientManager milvus = mock(MilvusClientManager.class);
        QuestionHitRecordMapper hitRecordMapper = mock(QuestionHitRecordMapper.class);
        when(milvus.size()).thenReturn(10);
        when(milvus.search(any(), eq("考务通知"), anyInt())).thenReturn(List.of());
        when(milvus.search(any(), eq("考务"), anyInt())).thenReturn(List.of());
        when(milvus.search(any(), eq("考试"), anyInt())).thenReturn(List.of());
        when(milvus.search(any(), eq("考试通知"), anyInt())).thenReturn(List.of());
        when(milvus.search(any(), eq("四六级"), anyInt())).thenReturn(List.of());
        when(milvus.search(any(), eq(null), anyInt())).thenReturn(List.of(document(2L, "教学运行")));
        when(hitRecordMapper.insert(any(QuestionHitRecord.class))).thenReturn(1);

        VectorSearchServiceImpl service = service(milvus, hitRecordMapper);

        VectorSearchResponse response = service.search("四级考试报名时间是什么时候？", "考务通知", 3, 1L, 1L);

        assertEquals(0.82, response.topScore());
        assertEquals(2L, response.topKnowledgeId());
        verify(milvus).search(any(), eq(null), anyInt());
    }

    @Test
    void multipleModuleCandidatesAreMergedBeforeReranking() {
        MilvusClientManager milvus = mock(MilvusClientManager.class);
        QuestionHitRecordMapper hitRecordMapper = mock(QuestionHitRecordMapper.class);
        when(milvus.size()).thenReturn(10);
        when(milvus.search(any(), eq("分类甲"), anyInt())).thenReturn(List.of(document(1L, "分类甲")));
        when(milvus.search(any(), eq("分类乙"), anyInt())).thenReturn(List.of(document(2L, "分类乙")));
        when(hitRecordMapper.insert(any(QuestionHitRecord.class))).thenReturn(1);

        VectorSearchResponse response = service(milvus, hitRecordMapper)
                .search("含义不明确的问题", List.of("分类甲", "分类乙"), 3, 1L, 1L);

        assertEquals(2, response.results().size());
        verify(milvus).search(any(), eq("分类甲"), anyInt());
        verify(milvus).search(any(), eq("分类乙"), anyInt());
    }

    private static VectorSearchServiceImpl service(MilvusClientManager milvus,
                                                   QuestionHitRecordMapper hitRecordMapper) {
        EmbeddingService embeddingService = new EmbeddingService() {
            @Override
            public float[] embed(String text) {
                return new float[]{1.0f, 0.0f};
            }

            @Override
            public int dimension() {
                return 2;
            }
        };
        RerankService rerankService = (query, queryVector, candidates, topK) -> candidates.stream()
                .limit(topK)
                .map(candidate -> new VectorSearchResult(
                        candidate.knowledgeId(),
                        candidate.question(),
                        candidate.answer(),
                        candidate.moduleType(),
                        candidate.categoryL1Id(),
                        candidate.categoryL2Id(),
                        candidate.categoryL3Id(),
                        candidate.categoryPath(),
                        candidate.sourceType(),
                        candidate.sourceUrl(),
                        0.90,
                        0.80,
                        0.82
                ))
                .toList();
        HitRuleEngine hitRuleEngine = score -> score >= 0.50
                ? new HitDecision(1, "弱命中")
                : new HitDecision(0, "未命中");
        return new VectorSearchServiceImpl(
                mock(KbQaEntryService.class),
                embeddingService,
                milvus,
                rerankService,
                hitRuleEngine,
                hitRecordMapper,
                20,
                3
        );
    }

    private static VectorDocument document(Long id, String moduleType) {
        return new VectorDocument(
                id,
                "四级考试报名时间是什么时候？",
                "四级考试报名以学校通知为准。",
                moduleType,
                1L,
                null,
                null,
                "考试",
                null,
                null,
                new float[]{1.0f, 0.0f}
        );
    }
}
