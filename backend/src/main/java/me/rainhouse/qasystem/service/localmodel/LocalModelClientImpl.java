package me.rainhouse.qasystem.service.localmodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.rainhouse.qasystem.config.LocalModelServiceProperties;
import me.rainhouse.qasystem.common.utils.AiTextSanitizer;
import me.rainhouse.qasystem.service.kb.FaqItem;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LocalModelClientImpl implements LocalModelClient {

    private final LocalModelServiceProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocalModelClientImpl(LocalModelServiceProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeoutMs());
        requestFactory.setReadTimeout(properties.getTimeoutMs());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public float[] embed(String text) {
        JsonNode node = post("/embed", Map.of("texts", List.of(text == null ? "" : text)));
        JsonNode vectorNode = node.path("embeddings").path(0);
        float[] vector = new float[vectorNode.size()];
        for (int i = 0; i < vectorNode.size(); i++) {
            vector[i] = (float) vectorNode.get(i).asDouble();
        }
        return vector;
    }

    @Override
    public List<Double> rerank(String query, List<String> documents) {
        JsonNode node = post("/rerank", Map.of(
                "query", query == null ? "" : query,
                "documents", documents == null ? List.of() : documents
        ));
        List<Double> scores = new ArrayList<>();
        node.path("scores").forEach(score -> scores.add(score.asDouble()));
        return scores;
    }

    @Override
    public String rewrite(String query) {
        String rewrite = post("/rewrite", Map.of("query", query == null ? "" : query))
                .path("rewrite")
                .asText("");
        return AiTextSanitizer.stripThinking(rewrite);
    }

    @Override
    public String classify(String query, List<String> candidateModules) {
        return classifyNode(query, candidateModules).path("moduleType").asText(null);
    }

    @Override
    public Map<String, Double> classifyScores(String query, List<String> candidateModules) {
        JsonNode scoresNode = classifyNode(query, candidateModules).path("scores");
        Map<String, Double> scores = new LinkedHashMap<>();
        scoresNode.fields().forEachRemaining(field -> scores.put(field.getKey(), field.getValue().asDouble()));
        return scores;
    }

    private JsonNode classifyNode(String query, List<String> candidateModules) {
        return post("/classify", Map.of(
                "query", query == null ? "" : query,
                "candidates", candidateModules == null ? List.of() : candidateModules
        ));
    }

    @Override
    public String generate(String originalQuestion, String rewriteQuestion, List<VectorSearchResult> references) {
        List<Map<String, Object>> refPayload = new ArrayList<>();
        if (references != null) {
            for (VectorSearchResult reference : references) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("knowledgeId", reference.knowledgeId());
                item.put("question", reference.question());
                item.put("answer", reference.answer());
                item.put("categoryPath", reference.categoryPath());
                item.put("sourceUrl", reference.sourceUrl());
                item.put("score", reference.finalScore());
                refPayload.add(item);
            }
        }

        JsonNode node = post("/generate", Map.of(
                "originalQuestion", originalQuestion == null ? "" : originalQuestion,
                "rewriteQuestion", rewriteQuestion == null ? "" : rewriteQuestion,
                "references", refPayload
        ));
        return AiTextSanitizer.stripThinking(node.path("answer").asText(""));
    }

    @Override
    public String psychologicalCounseling(String studentMsg) {
        if (!enabled()) {
            return LocalModelClient.super.psychologicalCounseling(studentMsg);
        }
        String answer = post("/psychological", Map.of("studentMsg", studentMsg == null ? "" : studentMsg))
                .path("answer")
                .asText("");
        answer = AiTextSanitizer.stripThinking(answer);
        return answer.isBlank() ? LocalModelClient.super.psychologicalCounseling(studentMsg) : answer;
    }

    @Override
    public List<FaqItem> chunkToFaq(String text, String title, int maxItems) {
        return chunkToFaq(text, title, maxItems, List.of());
    }

    @Override
    public List<FaqItem> chunkToFaq(String text, String title, int maxItems, List<String> categoryPaths) {
        JsonNode node = post("/chunk", Map.of(
                "text", text == null ? "" : text,
                "title", title == null ? "" : title,
                "maxItems", Math.max(1, maxItems),
                "categoryPaths", categoryPaths == null ? List.of() : categoryPaths
        ));
        List<FaqItem> items = new ArrayList<>();
        node.path("items").forEach(item -> {
            String categoryL1 = item.path("categoryL1").asText("").trim();
            String categoryL2 = item.path("categoryL2").asText("").trim();
            String categoryL3 = item.path("categoryL3").asText("").trim();
            String question = item.path("question").asText("").trim();
            String answer = item.path("answer").asText("").trim();
            if (!question.isEmpty() && !answer.isEmpty()) {
                items.add(new FaqItem(
                        categoryL1.isEmpty() ? null : categoryL1,
                        categoryL2.isEmpty() ? null : categoryL2,
                        categoryL3.isEmpty() ? null : categoryL3,
                        question,
                        answer
                ));
            }
        });
        return items;
    }

    @Override
    public boolean enabled() {
        return properties.isEnabled();
    }

    private JsonNode post(String path, Object body) {
        if (!enabled()) {
            throw new IllegalStateException("本地模型服务未启用");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            String response = restTemplate.exchange(url(path), HttpMethod.POST, entity, String.class).getBody();
            return objectMapper.readTree(response == null ? "{}" : response);
        } catch (Exception e) {
            throw new IllegalStateException("调用本地模型服务失败: " + path + "，请确认 model-service 已启动", e);
        }
    }

    private String url(String path) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
    }
}
