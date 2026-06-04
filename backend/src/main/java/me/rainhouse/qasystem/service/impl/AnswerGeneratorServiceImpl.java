package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.service.AnswerGeneratorService;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
import me.rainhouse.qasystem.service.vector.VectorSearchResponse;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AnswerGeneratorServiceImpl implements AnswerGeneratorService {

    private final LocalModelClient localModelClient;

    public AnswerGeneratorServiceImpl(AiModelProperties aiModelProperties,
                                      LocalModelClient localModelClient) {
        this.localModelClient = localModelClient;
        aiModelProperties.getQwenGeneratorPath();
    }

    @Override
    public String generate(String originalQuestion, String rewriteQuestion, VectorSearchResponse searchResponse) {
        if (searchResponse == null || searchResponse.results() == null || searchResponse.results().isEmpty()) {
            return null;
        }
        if (searchResponse.hitStatus() == null || searchResponse.hitStatus() == 0) {
            return null;
        }

        if (localModelClient.enabled()) {
            String answer = localModelClient.generate(originalQuestion, rewriteQuestion, searchResponse.results());
            if (StringUtils.hasText(answer)) {
                return answer;
            }
        }

        VectorSearchResult top = searchResponse.results().get(0);
        if (!StringUtils.hasText(top.answer())) {
            return null;
        }
        StringBuilder answer = new StringBuilder();
        answer.append(top.answer().trim());

        List<VectorSearchResult> references = searchResponse.results();
        if (!references.isEmpty()) {
            answer.append("\n\n参考来源：");
            for (int i = 0; i < Math.min(3, references.size()); i++) {
                VectorSearchResult result = references.get(i);
                answer.append("\n").append(i + 1)
                        .append(". 知识库#").append(result.knowledgeId())
                        .append("「").append(result.question()).append("」")
                        .append("，匹配分 ").append(String.format("%.4f", result.finalScore()));
            }
        }
        return answer.toString();
    }
}
