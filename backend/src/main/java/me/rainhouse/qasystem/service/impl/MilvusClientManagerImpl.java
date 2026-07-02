package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.entity.KbQaEntry;
import me.rainhouse.qasystem.service.MilvusClientManager;
import me.rainhouse.qasystem.service.KbQaEntryService;
import me.rainhouse.qasystem.service.vector.VectorDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MilvusClientManagerImpl implements MilvusClientManager {

    private static final int VARCHAR_QUESTION_MAX_LENGTH = 1000;
    private static final int VARCHAR_MODULE_TYPE_MAX_LENGTH = 50;
    private static final int VARCHAR_CATEGORY_PATH_MAX_LENGTH = 300;

    private final Gson gson = new Gson();
    private final MilvusClientV2 client;
    private final boolean available;
    private final KbQaEntryService kbQaEntryService;
    private final String collectionName;
    private final String idField;
    private final String embeddingField;
    private final String moduleTypeField;
    private final String questionField;
    private final String knowledgeIdField;
    private final String categoryL1IdField;
    private final String categoryL2IdField;
    private final String categoryL3IdField;
    private final String categoryPathField;
    private final String statusField;

    public MilvusClientManagerImpl(KbQaEntryService kbQaEntryService,
                                   @Value("${milvus.endpoint}") String endpoint,
                                   @Value("${milvus.api-key:}") String apiKey,
                                   @Value("${milvus.database-name:}") String databaseName,
                                   @Value("${milvus.collection-name:knowledge_vector_v2}") String collectionName,
                                   @Value("${milvus.field.id:id}") String idField,
                                   @Value("${milvus.field.embedding:embedding}") String embeddingField,
                                   @Value("${milvus.field.module-type:module_type}") String moduleTypeField,
                                   @Value("${milvus.field.question:question}") String questionField,
                                   @Value("${milvus.field.knowledge-id:knowledge_id}") String knowledgeIdField,
                                   @Value("${milvus.field.category-l1-id:category_l1_id}") String categoryL1IdField,
                                   @Value("${milvus.field.category-l2-id:category_l2_id}") String categoryL2IdField,
                                   @Value("${milvus.field.category-l3-id:category_l3_id}") String categoryL3IdField,
                                   @Value("${milvus.field.category-path:category_path}") String categoryPathField,
                                   @Value("${milvus.field.status:status}") String statusField) {
        this.kbQaEntryService = kbQaEntryService;
        this.collectionName = collectionName;
        this.idField = idField;
        this.embeddingField = embeddingField;
        this.moduleTypeField = moduleTypeField;
        this.questionField = questionField;
        this.knowledgeIdField = knowledgeIdField;
        this.categoryL1IdField = categoryL1IdField;
        this.categoryL2IdField = categoryL2IdField;
        this.categoryL3IdField = categoryL3IdField;
        this.categoryPathField = categoryPathField;
        this.statusField = statusField;

        MilvusClientV2 initializedClient = null;
        boolean initializedAvailable = false;
        try {
            ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                    .uri(endpoint);
            if (StringUtils.hasText(apiKey)) {
                builder.token(apiKey);
            }
            if (StringUtils.hasText(databaseName)) {
                builder.dbName(databaseName);
            }
            initializedClient = new MilvusClientV2(builder.build());
            initializedAvailable = true;
            log.info("Milvus 客户端初始化完成，endpoint: {}, auth: {}, collection: {}, database: {}",
                    endpoint,
                    StringUtils.hasText(apiKey) ? "token" : "none",
                    collectionName,
                    StringUtils.hasText(databaseName) ? databaseName : "default");
        } catch (Exception ex) {
            log.warn("Milvus is unavailable; vector database features are disabled, but the backend will keep running. endpoint: {}, error: {}",
                    endpoint,
                    ex.getMessage());
            log.debug("Milvus initialization failed", ex);
        }
        this.client = initializedClient;
        this.available = initializedAvailable;
    }

    @Override
    public void upsert(VectorDocument document) {
        if (!available) {
            return;
        }
        if (document == null || document.knowledgeId() == null) {
            return;
        }
        client.upsert(UpsertReq.builder()
                .collectionName(collectionName)
                .data(Collections.singletonList(toMilvusRow(document)))
                .build());
    }

    @Override
    public void upsertBatch(Collection<VectorDocument> documents) {
        if (!available) {
            return;
        }
        if (documents == null || documents.isEmpty()) {
            return;
        }
        List<JsonObject> rows = documents.stream()
                .filter(Objects::nonNull)
                .filter(document -> document.knowledgeId() != null)
                .map(this::toMilvusRow)
                .toList();
        if (rows.isEmpty()) {
            return;
        }
        client.upsert(UpsertReq.builder()
                .collectionName(collectionName)
                .data(rows)
                .build());
    }

    @Override
    public void remove(Long knowledgeId) {
        if (!available) {
            return;
        }
        if (knowledgeId == null) {
            return;
        }
        client.delete(DeleteReq.builder()
                .collectionName(collectionName)
                .ids(Collections.singletonList(knowledgeId))
                .build());
    }

    @Override
    public void clear() {
        if (!available) {
            return;
        }
        client.delete(DeleteReq.builder()
                .collectionName(collectionName)
                .filter(idField + " >= 0")
                .build());
    }

    @Override
    public int size() {
        if (!available) {
            return 0;
        }
        GetCollectionStatsResp stats = client.getCollectionStats(GetCollectionStatsReq.builder()
                .collectionName(collectionName)
                .build());
        long count = stats.getNumOfEntities();
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    @Override
    public List<VectorDocument> search(float[] queryVector, String moduleType, int topK) {
        if (!available) {
            return Collections.emptyList();
        }
        SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .collectionName(collectionName)
                .annsField(embeddingField)
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .topK(Math.max(1, topK))
                .outputFields(List.of(
                        idField,
                        knowledgeIdField,
                        questionField,
                        moduleTypeField,
                        categoryL1IdField,
                        categoryL2IdField,
                        categoryL3IdField,
                        categoryPathField,
                        statusField,
                        embeddingField
                ))
                .searchParams(Map.of("metric_type", "COSINE"));
        if (StringUtils.hasText(moduleType)) {
            builder.filter(moduleTypeField + " == \"" + escapeMilvusString(moduleType) + "\"");
        }

        SearchResp response = client.search(builder.build());
        List<SearchResp.SearchResult> hits = response.getSearchResults().isEmpty()
                ? Collections.emptyList()
                : response.getSearchResults().get(0);
        Map<Long, KbQaEntry> entries = loadEntries(hits);

        return hits.stream()
                .map(hit -> toVectorDocument(hit, entries))
                .filter(Objects::nonNull)
                .toList();
    }

    private JsonObject toMilvusRow(VectorDocument document) {
        JsonObject row = new JsonObject();
        row.addProperty(idField, document.knowledgeId());
        row.addProperty(knowledgeIdField, document.knowledgeId());
        row.addProperty(questionField, truncate(document.question(), VARCHAR_QUESTION_MAX_LENGTH));
        row.addProperty(moduleTypeField, truncate(document.moduleType(), VARCHAR_MODULE_TYPE_MAX_LENGTH));
        row.addProperty(categoryL1IdField, document.categoryL1Id() == null ? 0L : document.categoryL1Id());
        row.addProperty(categoryL2IdField, document.categoryL2Id() == null ? 0L : document.categoryL2Id());
        row.addProperty(categoryL3IdField, document.categoryL3Id() == null ? 0L : document.categoryL3Id());
        row.addProperty(categoryPathField, truncate(document.categoryPath(), VARCHAR_CATEGORY_PATH_MAX_LENGTH));
        row.addProperty(statusField, 1L);
        row.add(embeddingField, gson.toJsonTree(toFloatList(document.vector())));
        return row;
    }

    private VectorDocument toVectorDocument(SearchResp.SearchResult hit, Map<Long, KbQaEntry> entries) {
        Long knowledgeId = longValue(hit.getEntity().getOrDefault(knowledgeIdField, hit.getId()));
        if (knowledgeId == null) {
            return null;
        }
        KbQaEntry entry = entries.get(knowledgeId);
        String question = stringValue(hit.getEntity().get(questionField));
        String moduleType = stringValue(hit.getEntity().get(moduleTypeField));
        Long categoryL1Id = longValue(hit.getEntity().get(categoryL1IdField));
        Long categoryL2Id = longValue(hit.getEntity().get(categoryL2IdField));
        Long categoryL3Id = longValue(hit.getEntity().get(categoryL3IdField));
        String categoryPath = stringValue(hit.getEntity().get(categoryPathField));
        if (entry != null) {
            question = StringUtils.hasText(entry.getQuestion()) ? entry.getQuestion() : question;
            moduleType = StringUtils.hasText(entry.getModuleType()) ? entry.getModuleType() : moduleType;
            categoryL1Id = entry.getCategoryL1Id() == null ? categoryL1Id : entry.getCategoryL1Id();
            categoryL2Id = entry.getCategoryL2Id() == null ? categoryL2Id : entry.getCategoryL2Id();
            categoryL3Id = entry.getCategoryL3Id() == null ? categoryL3Id : entry.getCategoryL3Id();
            categoryPath = StringUtils.hasText(categoryPath(entry)) ? categoryPath(entry) : categoryPath;
        }
        return new VectorDocument(
                knowledgeId,
                question,
                entry == null ? "" : entry.getAnswer(),
                moduleType,
                zeroToNull(categoryL1Id),
                zeroToNull(categoryL2Id),
                zeroToNull(categoryL3Id),
                categoryPath,
                entry == null ? null : entry.getSourceType(),
                entry == null ? null : entry.getSourceUrl(),
                floatArray(hit.getEntity().get(embeddingField))
        );
    }

    private Map<Long, KbQaEntry> loadEntries(List<SearchResp.SearchResult> hits) {
        List<Long> knowledgeIds = hits.stream()
                .map(hit -> longValue(hit.getEntity().getOrDefault(knowledgeIdField, hit.getId())))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (knowledgeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return kbQaEntryService.list(new LambdaQueryWrapper<KbQaEntry>()
                        .in(KbQaEntry::getId, knowledgeIds))
                .stream()
                .collect(Collectors.toMap(KbQaEntry::getId, Function.identity(), (left, right) -> left));
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> values = new ArrayList<>(vector == null ? 0 : vector.length);
        if (vector == null) {
            return values;
        }
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Long.parseLong(text);
        }
        return null;
    }

    private Long zeroToNull(Long value) {
        return value == null || value == 0L ? null : value;
    }

    private String categoryPath(KbQaEntry entry) {
        if (entry == null) {
            return "";
        }
        return java.util.stream.Stream.of(
                        entry.getCategoryL1Name(),
                        entry.getCategoryL2Name(),
                        entry.getCategoryL3Name()
                )
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" > "));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private float[] floatArray(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        float[] values = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof Number number) {
                values[i] = number.floatValue();
            }
        }
        return values;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String escapeMilvusString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
