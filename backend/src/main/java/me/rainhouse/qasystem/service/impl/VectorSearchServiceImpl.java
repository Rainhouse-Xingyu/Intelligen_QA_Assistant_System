package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.entity.KbQaEntry;
import me.rainhouse.qasystem.entity.QuestionHitRecord;
import me.rainhouse.qasystem.mapper.QuestionHitRecordMapper;
import me.rainhouse.qasystem.service.EmbeddingService;
import me.rainhouse.qasystem.service.HitRuleEngine;
import me.rainhouse.qasystem.service.KbQaEntryService;
import me.rainhouse.qasystem.service.MilvusClientManager;
import me.rainhouse.qasystem.service.RerankService;
import me.rainhouse.qasystem.service.VectorSearchService;
import me.rainhouse.qasystem.service.vector.HitDecision;
import me.rainhouse.qasystem.service.vector.VectorDocument;
import me.rainhouse.qasystem.service.vector.VectorSearchResponse;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
public class VectorSearchServiceImpl implements VectorSearchService {

    private final KbQaEntryService kbQaEntryService;
    private final EmbeddingService embeddingService;
    private final MilvusClientManager milvusClientManager;
    private final RerankService rerankService;
    private final HitRuleEngine hitRuleEngine;
    private final QuestionHitRecordMapper questionHitRecordMapper;
    private final int topCandidates;
    private final int defaultTopResults;

    public VectorSearchServiceImpl(KbQaEntryService kbQaEntryService,
                                   EmbeddingService embeddingService,
                                   MilvusClientManager milvusClientManager,
                                   RerankService rerankService,
                                   HitRuleEngine hitRuleEngine,
                                   QuestionHitRecordMapper questionHitRecordMapper,
                                   @Value("${vector.search.top-candidates:20}") int topCandidates,
                                   @Value("${vector.search.top-results:3}") int defaultTopResults) {
        this.kbQaEntryService = kbQaEntryService;
        this.embeddingService = embeddingService;
        this.milvusClientManager = milvusClientManager;
        this.rerankService = rerankService;
        this.hitRuleEngine = hitRuleEngine;
        this.questionHitRecordMapper = questionHitRecordMapper;
        this.topCandidates = topCandidates;
        this.defaultTopResults = defaultTopResults;
    }

    @Override
    public synchronized int rebuildIndex() {
        List<KbQaEntry> enabledEntries = kbQaEntryService.list(new LambdaQueryWrapper<KbQaEntry>()
                .eq(KbQaEntry::getStatus, 1));
        milvusClientManager.clear();
        upsertEntries(enabledEntries);
        log.info("向量索引重建完成，启用词条数: {}", milvusClientManager.size());
        return milvusClientManager.size();
    }

    @Override
    public void upsertEntry(KbQaEntry entry) {
        if (entry == null || entry.getId() == null) {
            return;
        }
        if (entry.getStatus() != null && entry.getStatus() == 0) {
            removeEntry(entry.getId());
            return;
        }
        String text = entry.getQuestion() + "\n" + entry.getAnswer();
        milvusClientManager.upsert(VectorDocument.from(entry, embeddingService.embed(text)));
    }

    @Override
    public void upsertEntries(Collection<KbQaEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        milvusClientManager.upsertBatch(entries.stream()
                .filter(entry -> entry.getId() != null)
                .filter(entry -> entry.getStatus() == null || entry.getStatus() == 1)
                .map(entry -> VectorDocument.from(entry, embeddingService.embed(entry.getQuestion() + "\n" + entry.getAnswer())))
                .toList());
    }

    @Override
    public void removeEntry(Long knowledgeId) {
        milvusClientManager.remove(knowledgeId);
    }

    @Override
    public VectorSearchResponse search(String query, String moduleType, Integer topK, Long userId, Long sessionId) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("检索问题不能为空");
        }
        long start = System.currentTimeMillis();
        ensureIndexReady();

        int resultLimit = topK == null || topK <= 0 ? defaultTopResults : topK;
        float[] queryVector = embeddingService.embed(query);
        List<VectorDocument> candidates = milvusClientManager.search(queryVector, moduleType, topCandidates);
        List<VectorSearchResult> results = rerankService.rerank(query, queryVector, candidates, resultLimit);
        VectorSearchResult topResult = results.isEmpty() ? null : results.get(0);

        double topScore = topResult == null ? 0.0 : topResult.finalScore();
        HitDecision hitDecision = hitRuleEngine.decide(topScore);
        long responseTimeMs = System.currentTimeMillis() - start;
        saveHitRecord(query, moduleType, userId, sessionId, topResult, hitDecision, responseTimeMs);

        return new VectorSearchResponse(
                query,
                moduleType,
                hitDecision.hitStatus(),
                hitDecision.hitLabel(),
                topScore,
                topResult == null ? null : topResult.knowledgeId(),
                hitDecision.hitStatus() == 0 || topResult == null ? null : topResult.answer(),
                responseTimeMs,
                results
        );
    }

    private void ensureIndexReady() {
        if (milvusClientManager.size() == 0) {
            rebuildIndex();
        }
    }

    private void saveHitRecord(String query,
                               String moduleType,
                               Long userId,
                               Long sessionId,
                               VectorSearchResult topResult,
                               HitDecision hitDecision,
                               long responseTimeMs) {
        QuestionHitRecord record = new QuestionHitRecord();
        record.setUserId(userId);
        record.setSessionId(sessionId);
        record.setOriginalQuestion(query);
        record.setRewriteQuestion(query);
        record.setModuleType(moduleType);
        record.setTopScore(BigDecimal.valueOf(topResult == null ? 0.0 : topResult.finalScore()).setScale(4, RoundingMode.HALF_UP));
        record.setHitStatus(hitDecision.hitStatus());
        record.setKnowledgeId(topResult == null ? null : topResult.knowledgeId());
        record.setResponseTimeMs(responseTimeMs);
        record.setCreatedAt(LocalDateTime.now());
        questionHitRecordMapper.insert(record);
    }
}
