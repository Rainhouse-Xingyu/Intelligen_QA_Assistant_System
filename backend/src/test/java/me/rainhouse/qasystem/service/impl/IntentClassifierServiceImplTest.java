package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentClassifierServiceImplTest {

    @Test
    void currentQuestionKeywordOverridesContextAndModelMisclassification() {
        IntentClassifierServiceImpl service = new IntentClassifierServiceImpl(
                new AiModelProperties(),
                new FixedClassifyLocalModelClient("学业帮扶")
        );
        String query = "对话上下文：\n"
                + "用户：挂科后学业预警和帮扶措施有哪些？\n"
                + "助手：关于学业帮扶的说明。\n"
                + "当前问题：四六级考试期间各考试内容时间段分配的时间安排、截止要求和查询方式是什么？";

        String module = service.classify(query, null);

        assertEquals("考务通知", module);
    }

    @Test
    void followUpWithoutCurrentKeywordCanUseModelClassification() {
        IntentClassifierServiceImpl service = new IntentClassifierServiceImpl(
                new AiModelProperties(),
                new FixedClassifyLocalModelClient("学业帮扶")
        );

        String module = service.classify("对话上下文：用户：挂科后有什么帮扶？\n当前问题：截止时间是什么？", null);

        assertEquals("学业帮扶", module);
    }

    private static class FixedClassifyLocalModelClient implements LocalModelClient {
        private final String moduleType;

        private FixedClassifyLocalModelClient(String moduleType) {
            this.moduleType = moduleType;
        }

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
            return moduleType;
        }

        @Override
        public String generate(String originalQuestion, String rewriteQuestion, List<VectorSearchResult> references) {
            return "";
        }

        @Override
        public boolean enabled() {
            return true;
        }
    }
}
