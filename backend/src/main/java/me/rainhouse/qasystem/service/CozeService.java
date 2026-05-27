package me.rainhouse.qasystem.service;

public interface CozeService {

    /**
     * 发送文本消息给 Coze 问答工作流并同步获取回复。
     * 优先调用 Inquiry_Intent_Recognition，未命中时自动调用 Unknown_Business_Exception。
     * @param userId 调用者的用户ID（方便Coze维持上下文）
     * @param query 用户发送的文本问题
     * @return AI 大模型返回的解答内容
     */
    String chat(String userId, String query);

    /**
     * 调用 Custom_Learning_Resources 工作流生成个性化学习资源与帮扶方案。
     */
    String generateLearningResources(String studentId,
                                     String weakKnowledge,
                                     String warningLevel,
                                     String surveyIndicator);

    /**
     * 调用 Psychological_Counseling 工作流生成心理疏导回复。
     */
    String psychologicalCounseling(String studentId, String studentMsg);
}
