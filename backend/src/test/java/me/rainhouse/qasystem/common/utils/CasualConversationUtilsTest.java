package me.rainhouse.qasystem.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasualConversationUtilsTest {

    @Test
    void detectsPureCasualPhrases() {
        assertTrue(CasualConversationUtils.isCasualOnly("你好"));
        assertTrue(CasualConversationUtils.isCasualOnly("拜拜啦"));
        assertTrue(CasualConversationUtils.isCasualOnly("谢谢"));
        assertTrue(CasualConversationUtils.isCasualOnly("谢啦"));
        assertTrue(CasualConversationUtils.isCasualOnly("麻烦啦"));
        assertTrue(CasualConversationUtils.isCasualOnly("有人不"));
        assertTrue(CasualConversationUtils.isCasualOnly("嗯嗯"));
        assertTrue(CasualConversationUtils.isCasualOnly("你好你好。"));
    }

    @Test
    void keepsBusinessQuestionsInAiFlow() {
        assertFalse(CasualConversationUtils.isCasualOnly("你好，请问选课怎么弄"));
        assertFalse(CasualConversationUtils.isCasualOnly("谢谢，补考怎么报名"));
    }
}
