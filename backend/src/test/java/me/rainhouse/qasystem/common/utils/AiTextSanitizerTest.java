package me.rainhouse.qasystem.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiTextSanitizerTest {

    @Test
    void stripsCompleteThinkBlock() {
        assertEquals("正式回答", AiTextSanitizer.stripThinking("<think>内部推理</think>\n正式回答"));
    }

    @Test
    void stripsGeneratedFragmentWithoutOpeningThinkTag() {
        assertEquals("正式回答", AiTextSanitizer.stripThinking("内部推理\n</think>\n\n正式回答"));
    }

    @Test
    void stripsUnclosedThinkBlock() {
        assertEquals("", AiTextSanitizer.stripThinking("<think>内部推理"));
    }
}
