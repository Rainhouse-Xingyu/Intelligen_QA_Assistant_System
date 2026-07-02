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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Slf4j
@Service
public class VectorSearchServiceImpl implements VectorSearchService {

    private static final int LEXICAL_CANDIDATE_LIMIT = 40;
    private static final int MAX_QUERY_TERMS = 12;

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
        String text = embeddingText(entry);
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
                .map(entry -> VectorDocument.from(entry, embeddingService.embed(embeddingText(entry))))
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
        List<VectorDocument> vectorCandidates = milvusClientManager.search(queryVector, moduleType, topCandidates);
        List<VectorDocument> candidates = mergeCandidates(vectorCandidates, lexicalCandidates(query, moduleType));
        List<VectorSearchResult> results = rerankService.rerank(query, queryVector, candidates, resultLimit);
        //获取的是相似度最高的那个结果进行返回
        VectorSearchResult topResult = results.isEmpty() ? null : results.get(0);

        double topScore = topResult == null ? 0.0 : topResult.finalScore();
        // 根据得分和预设的规则引擎判断命中类型 目前是0.7
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

    private String embeddingText(KbQaEntry entry) {
        String question = entry.getQuestion() == null ? "" : entry.getQuestion().trim();
        String answer = entry.getAnswer() == null ? "" : entry.getAnswer().trim();
        String categoryPath = categoryPath(entry);
        return "分类路径：" + categoryPath + "\n问题：" + question + "\n问题：" + question + "\n答案：" + answer;
    }

    private String categoryPath(KbQaEntry entry) {
        return java.util.stream.Stream.of(
                        entry.getCategoryL1Name(),
                        entry.getCategoryL2Name(),
                        entry.getCategoryL3Name()
                )
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.joining(" > "));
    }

    private List<VectorDocument> lexicalCandidates(String query, String moduleType) {
        List<String> terms = extractQueryTerms(query);
        if (terms.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<KbQaEntry> wrapper = new LambdaQueryWrapper<KbQaEntry>()
                .eq(KbQaEntry::getStatus, 1)
                .last("limit " + LEXICAL_CANDIDATE_LIMIT);
        if (StringUtils.hasText(moduleType)) {
            String trimmedModule = moduleType.trim();
            wrapper.and(group -> group.eq(KbQaEntry::getModuleType, trimmedModule)
                    .or()
                    .eq(KbQaEntry::getCategoryL1Name, trimmedModule)
                    .or()
                    .eq(KbQaEntry::getCategoryL2Name, trimmedModule)
                    .or()
                    .eq(KbQaEntry::getCategoryL3Name, trimmedModule));
        }
        wrapper.and(group -> {
            for (int i = 0; i < terms.size(); i++) {
                String term = terms.get(i);
                if (i > 0) {
                    group.or();
                }
                group.like(KbQaEntry::getQuestion, term)
                        .or()
                        .like(KbQaEntry::getAnswer, term);
            }
        });

        return kbQaEntryService.list(wrapper).stream()
                .map(entry -> VectorDocument.from(entry, null))
                .toList();
    }

    private List<VectorDocument> mergeCandidates(List<VectorDocument> vectorCandidates,
                                                 List<VectorDocument> lexicalCandidates) {
        Map<Long, VectorDocument> merged = new LinkedHashMap<>();
        for (VectorDocument candidate : vectorCandidates) {
            if (candidate != null && candidate.knowledgeId() != null) {
                merged.put(candidate.knowledgeId(), candidate);
            }
        }
        for (VectorDocument candidate : lexicalCandidates) {
            if (candidate != null && candidate.knowledgeId() != null) {
                merged.putIfAbsent(candidate.knowledgeId(), candidate);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<String> extractQueryTerms(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String compact = query.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "");
        if (!StringUtils.hasText(compact)) {
            return List.of();
        }

        Set<String> terms = new HashSet<>();
        addTerm(terms, compact);
        for (String part : query.toLowerCase(Locale.ROOT).split("[\\p{Punct}\\p{IsPunctuation}\\s]+")) {
            addTerm(terms, part);
        }
        for (int gramSize : List.of(4, 3, 2)) {
            for (int i = 0; i + gramSize <= compact.length() && terms.size() < MAX_QUERY_TERMS; i++) {
                addTerm(terms, compact.substring(i, i + gramSize));
            }
        }
        return terms.stream().limit(MAX_QUERY_TERMS).toList();
    }

    private void addTerm(Set<String> terms, String term) {
        if (!StringUtils.hasText(term)) {
            return;
        }
        String cleanTerm = term.trim();
        if (cleanTerm.length() < 2 || cleanTerm.length() > 40) {
            return;
        }
        if (isWeakQueryTerm(cleanTerm)) {
            return;
        }
        terms.add(cleanTerm);
    }

    private boolean isWeakQueryTerm(String term) {
        return Set.of("什么", "怎么", "如何", "哪些", "是否", "可以", "需要", "规定", "政策", "要求")
                .contains(term);
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
