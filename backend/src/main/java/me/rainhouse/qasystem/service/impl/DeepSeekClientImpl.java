package me.rainhouse.qasystem.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.common.utils.AiTextSanitizer;
import me.rainhouse.qasystem.config.DeepSeekModelProperties;
import me.rainhouse.qasystem.service.DeepSeekClient;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DeepSeekClientImpl implements DeepSeekClient {

    private final DeepSeekModelProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeepSeekClientImpl(DeepSeekModelProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeoutMs());
        requestFactory.setReadTimeout(properties.getTimeoutMs());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public boolean enabled() {
        return properties.isEnabled() && StringUtils.hasText(properties.getApiKey());
    }

    @Override
    public String generate(String originalQuestion,
                           String rewriteQuestion,
                           String memoryContext,
                           List<VectorSearchResult> references) {
        if (!enabled()) {
            throw new IllegalStateException("DeepSeek 未启用或缺少 API Key");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", "你是大连东软信息学院智能问答助手。只能依据给定知识库参考回答，表达清晰、简洁，不编造政策。"
        ));
        messages.add(Map.of(
                "role", "user",
                "content", buildPrompt(originalQuestion, rewriteQuestion, memoryContext, references)
        ));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("max_tokens", properties.getMaxTokens());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        try {
            String response = restTemplate.exchange(
                    properties.getBaseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            ).getBody();
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String answer = root.path("choices").path(0).path("message").path("content").asText("");
            return AiTextSanitizer.stripThinking(answer);
        } catch (Exception ex) {
            log.warn("[AI] DeepSeek generate failed, will fallback to local model: {}", ex.getMessage());
            throw new IllegalStateException("调用 DeepSeek 失败", ex);
        }
    }

    private String buildPrompt(String originalQuestion,
                               String rewriteQuestion,
                               String memoryContext,
                               List<VectorSearchResult> references) {
        StringBuilder prompt = new StringBuilder();
        if (StringUtils.hasText(memoryContext)) {
            prompt.append("最近上下文：\n").append(memoryContext.trim()).append("\n\n");
        }
        prompt.append("学生原问题：").append(originalQuestion == null ? "" : originalQuestion).append("\n");
        prompt.append("标准化问题：").append(rewriteQuestion == null ? "" : rewriteQuestion).append("\n\n");
        prompt.append("知识库参考：\n");
        if (references != null) {
            for (int i = 0; i < references.size(); i++) {
                VectorSearchResult reference = references.get(i);
                prompt.append(i + 1)
                        .append(". 问：").append(reference.question()).append("\n")
                        .append("答：").append(reference.answer()).append("\n");
                if (StringUtils.hasText(reference.sourceUrl())) {
                    prompt.append("来源：").append(reference.sourceUrl()).append("\n");
                }
            }
        }
        prompt.append("\n请直接输出面向学生的最终答案，不要输出思考过程。");
        return prompt.toString();
    }
}
