package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.service.ChatMemoryService;
import me.rainhouse.qasystem.service.DeepSeekClient;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
import me.rainhouse.qasystem.service.vector.VectorSearchResponse;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnswerGeneratorServiceImplTest {

    @Test
    void localGenerationFailureFallsBackToKnowledgeAnswer() {
        LocalModelClient localModelClient = mock(LocalModelClient.class);
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        ChatMemoryService chatMemoryService = mock(ChatMemoryService.class);
        when(localModelClient.enabled()).thenReturn(true);
        when(localModelClient.generate(any(), any(), any())).thenThrow(new IllegalStateException("service unavailable"));
        when(deepSeekClient.enabled()).thenReturn(false);
        when(chatMemoryService.buildGenerationQuestion(any(), any())).thenReturn("问题");
        VectorSearchResult result = new VectorSearchResult(
                1L, "标准问题", "标准答案", "分类甲",
                1L, null, null, "分类甲", null, null,
                0.8, 0.8, 0.8);
        VectorSearchResponse response = new VectorSearchResponse(
                "问题", "分类甲", 1, "弱命中", 0.8,
                1L, "标准答案", 1L, List.of(result));
        AnswerGeneratorServiceImpl service = new AnswerGeneratorServiceImpl(
                new AiModelProperties(), localModelClient, chatMemoryService, deepSeekClient);

        String answer = service.generate("问题", "问题", response, "");

        assertTrue(answer.startsWith("标准答案"));
    }
}
