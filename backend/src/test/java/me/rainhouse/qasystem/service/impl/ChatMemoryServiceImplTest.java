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

    @Test
    void shortSelfContainedQuestionStartsNewTopic() {
        ChatMemoryServiceImpl service = new ChatMemoryServiceImpl(null, 12, 1200, 800);

        assertEquals("怎么请假", service.buildRetrievalQuery("怎么请假", "用户：重修如何报名？"));
        assertEquals("怎么请假", service.buildGenerationQuestion("怎么请假", "用户：重修如何报名？"));
    }

    @Test
    void subjectlessFollowUpUsesHistoryForRetrievalAndGeneration() {
        ChatMemoryServiceImpl service = new ChatMemoryServiceImpl(null, 12, 1200, 800);
        String memory = "用户：重修如何报名？";

        String retrievalQuery = service.buildRetrievalQuery("截止到什么时候？", memory);
        String generationQuestion = service.buildGenerationQuestion("截止到什么时候？", memory);

        assertTrue(retrievalQuery.contains("重修如何报名"));
        assertTrue(generationQuestion.contains("重修如何报名"));
    }

    @Test
    void completeNewQuestionDoesNotUseHistoryForGeneration() {
        ChatMemoryServiceImpl service = new ChatMemoryServiceImpl(null, 12, 1200, 800);

        String question = "最近总是睡不着怎么办？";

        assertEquals(question, service.buildGenerationQuestion(question, "用户：重修如何报名？"));
    }
}
