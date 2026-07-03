package me.rainhouse.qasystem.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMemoryServiceImplTest {

    @Test
    void clearTopicQuestionDoesNotAppendHistoryContext() {
        ChatMemoryServiceImpl service = new ChatMemoryServiceImpl(null, 12, 1200, 800);
        String query = "四六级考试期间各考试内容时间段分配的时间安排、截止要求和查询方式是什么？";
        String memory = "用户：挂科后学业预警和帮扶措施有哪些？\n助手：关于学业帮扶的说明。";

        String retrievalQuery = service.buildRetrievalQuery(query, memory);

        assertEquals(query, retrievalQuery);
    }

    @Test
    void shortTopicQuestionDoesNotAppendHistoryContext() {
        ChatMemoryServiceImpl service = new ChatMemoryServiceImpl(null, 12, 1200, 800);

        String retrievalQuery = service.buildRetrievalQuery("补考时间", "用户：选课怎么弄？");

        assertEquals("补考时间", retrievalQuery);
    }

    @Test
    void explicitReferenceQuestionKeepsHistoryContext() {
        ChatMemoryServiceImpl service = new ChatMemoryServiceImpl(null, 12, 1200, 800);

        String retrievalQuery = service.buildRetrievalQuery("这个流程是什么？", "用户：转专业怎么申请？");

        assertTrue(retrievalQuery.contains("对话上下文"));
        assertTrue(retrievalQuery.contains("转专业"));
        assertTrue(retrievalQuery.contains("当前问题：这个流程是什么？"));
    }
}
