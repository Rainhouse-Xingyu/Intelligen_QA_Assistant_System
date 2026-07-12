package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.entity.KbCategory;
import me.rainhouse.qasystem.mapper.KbCategoryMapper;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentClassifierServiceImplTest {

    @Test
    void classificationUsesCurrentQuestionAndDynamicCategoryTree() {
        KbCategoryMapper categoryMapper = categoryMapper();
        LocalModelClient localModelClient = mock(LocalModelClient.class);
        when(localModelClient.enabled()).thenReturn(true);
        when(localModelClient.classifyScores(any(), any())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            List<String> labels = invocation.getArgument(1);
            assertEquals("当前咨询内容", query);
            return Map.of(labels.get(0), 0.81, labels.get(1), 0.62);
        });
        IntentClassifierServiceImpl service = new IntentClassifierServiceImpl(
                new AiModelProperties(), localModelClient, categoryMapper);

        List<String> modules = service.classifyCandidates(
                "对话上下文：助手：不应参与分类\n当前问题：当前咨询内容", null);

        assertEquals(List.of("分类甲"), modules);
    }

    @Test
    void closeScoresReturnTwoCandidateModules() {
        KbCategoryMapper categoryMapper = categoryMapper();
        LocalModelClient localModelClient = mock(LocalModelClient.class);
        when(localModelClient.enabled()).thenReturn(true);
        when(localModelClient.classifyScores(any(), any())).thenAnswer(invocation -> {
            List<String> labels = invocation.getArgument(1);
            return Map.of(labels.get(0), 0.76, labels.get(1), 0.72);
        });
        IntentClassifierServiceImpl service = new IntentClassifierServiceImpl(
                new AiModelProperties(), localModelClient, categoryMapper);

        assertEquals(List.of("分类甲", "分类乙"), service.classifyCandidates("含义不明确的问题", null));
    }

    private static KbCategoryMapper categoryMapper() {
        KbCategoryMapper mapper = mock(KbCategoryMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                category(1L, null, "分类甲", 1),
                category(2L, 1L, "子类甲", 2),
                category(3L, null, "分类乙", 1),
                category(4L, 3L, "子类乙", 2)
        ));
        return mapper;
    }

    private static KbCategory category(Long id, Long parentId, String name, int level) {
        KbCategory category = new KbCategory();
        category.setId(id);
        category.setParentId(parentId);
        category.setName(name);
        category.setLevel(level);
        category.setSortOrder(0);
        category.setStatus(1);
        return category;
    }
}
