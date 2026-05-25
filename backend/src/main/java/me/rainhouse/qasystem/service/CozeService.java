package me.rainhouse.qasystem.service;

public interface CozeService {

    /**
     * 发送文本消息给 Coze 意图网关并同步获取回复
     * @param userId 调用者的用户ID（方便Coze维持上下文）
     * @param query 用户发送的文本问题
     * @return AI 大模型返回的解答内容
     */
    String chat(String userId, String query);
}