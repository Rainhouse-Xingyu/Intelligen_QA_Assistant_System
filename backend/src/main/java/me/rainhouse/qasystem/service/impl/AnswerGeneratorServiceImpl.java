package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.service.AnswerGeneratorService;
import me.rainhouse.qasystem.service.vector.VectorSearchResponse;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnswerGeneratorServiceImpl implements AnswerGeneratorService {

    public AnswerGeneratorServiceImpl(AiModelProperties aiModelProperties) {
        // 预留 Qwen3.5-4B 本地答案生成模型接入点；当前实现保持纯 Java 可运行。
        String ignoredModelPath = aiModelProperties.getQwenGeneratorPath();
    }

    @Override
    public String generate(String originalQuestion, String rewriteQuestion, VectorSearchResponse searchResponse) {
        if (searchResponse == null || searchResponse.results() == null || searchResponse.results().isEmpty()) {
            return null;
        }
        if (searchResponse.hitStatus() == null || searchResponse.hitStatus() == 0) {
            return null;
        }

        VectorSearchResult top = searchResponse.results().get(0);
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
