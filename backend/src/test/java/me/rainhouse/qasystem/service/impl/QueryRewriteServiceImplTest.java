package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryRewriteServiceImplTest {

    @Test
    void fallbackRewriteExpandsShortKeywordQuestion() {
        QueryRewriteServiceImpl service = new QueryRewriteServiceImpl(new AiModelProperties(), new DisabledLocalModelClient());

        String rewritten = service.rewrite("补考");

        assertNotEquals("补考？", rewritten);
        assertTrue(rewritten.contains("补考"));
        assertTrue(rewritten.contains("政策") || rewritten.contains("办理") || rewritten.contains("注意事项"));
    }

    @Test
    void fallbackRewriteKeepsUserTopicAndCompletesProcessIntent() {
        QueryRewriteServiceImpl service = new QueryRewriteServiceImpl(new AiModelProperties(), new DisabledLocalModelClient());

        String rewritten = service.rewrite("你好，请问选课怎么弄");

        assertTrue(rewritten.contains("选课"));
        assertTrue(rewritten.contains("办理流程") || rewritten.contains("操作流程"));
        assertTrue(rewritten.endsWith("？"));
    }

    private static class DisabledLocalModelClient implements LocalModelClient {
        @Override
        public float[] embed(String text) {
            return new float[0];
        }

        @Override
        public List<Double> rerank(String query, List<String> documents) {
            return List.of();
        }

        @Override
        public String rewrite(String query) {
            return "";
        }

        @Override
        public String classify(String query, List<String> candidateModules) {
            return "";
        }

        @Override
        public String generate(String originalQuestion, String rewriteQuestion, List<VectorSearchResult> references) {
            return "";
        }

        @Override
        public boolean enabled() {
            return false;
        }
    }
}
