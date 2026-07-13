package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void casualPhraseBypassesLocalModelRewrite() {
        QueryRewriteServiceImpl service = new QueryRewriteServiceImpl(new AiModelProperties(), new ThrowingEnabledLocalModelClient());

        String rewritten = service.rewrite("你好");

        assertEquals("你好", rewritten);
    }

    @Test
    void repeatedGreetingDoesNotBecomeEmptyRetrievalQuery() {
        QueryRewriteServiceImpl service = new QueryRewriteServiceImpl(new AiModelProperties(), new DisabledLocalModelClient());

        assertEquals("你好你好。", service.rewrite("你好你好。"));
    }

    @Test
    void rejectsLocalModelRewriteWhenTopicDrifts() {
        QueryRewriteServiceImpl service = new QueryRewriteServiceImpl(
                new AiModelProperties(),
                new FixedRewriteLocalModelClient("选课的开放时间、操作流程和退补选规则是什么？")
        );

        String rewritten = service.rewrite("我想问问线上考试有什么要求");

        assertTrue(rewritten.contains("线上考试") || rewritten.contains("考试"));
        assertTrue(rewritten.contains("要求") || rewritten.contains("注意事项"));
        assertTrue(!rewritten.contains("选课"));
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

    private static class ThrowingEnabledLocalModelClient extends DisabledLocalModelClient {
        @Override
        public String rewrite(String query) {
            throw new AssertionError("casual phrases should not call local model rewrite");
        }

        @Override
        public boolean enabled() {
            return true;
        }
    }

    private static class FixedRewriteLocalModelClient extends DisabledLocalModelClient {
        private final String rewritten;

        private FixedRewriteLocalModelClient(String rewritten) {
            this.rewritten = rewritten;
        }

        @Override
        public String rewrite(String query) {
            return rewritten;
        }

        @Override
        public boolean enabled() {
            return true;
        }
    }
}
