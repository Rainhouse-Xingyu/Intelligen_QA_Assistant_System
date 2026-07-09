package me.rainhouse.qasystem.service.localmodel;

import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import me.rainhouse.qasystem.service.kb.FaqItem;

import java.util.List;

public interface LocalModelClient {

    static String superFallbackPsychologicalCounseling(String studentMsg) {
        return "我听到了，先别急着一个人硬扛。你可以先给自己一点缓冲时间，喝口水、深呼吸几次，然后把眼前最烦的一件事写下来，先处理最小的一步。要是这种状态持续影响睡眠、学习或者生活，也很建议你找辅导员、朋友或学校心理咨询老师聊聊。";
    }

    float[] embed(String text);

    List<Double> rerank(String query, List<String> documents);

    String rewrite(String query);

    String classify(String query, List<String> candidateModules);

    String generate(String originalQuestion, String rewriteQuestion, List<VectorSearchResult> references);

    default String psychologicalCounseling(String studentMsg) {
        return superFallbackPsychologicalCounseling(studentMsg);
    }

    default List<FaqItem> chunkToFaq(String text, String title, int maxItems) {
        return List.of();
    }

    default List<FaqItem> chunkToFaq(String text, String title, int maxItems, List<String> categoryPaths) {
        return chunkToFaq(text, title, maxItems);
    }

    boolean enabled();
}
