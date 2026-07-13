package me.rainhouse.qasystem.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnswerTextSanitizerTest {

    @Test
    void removesReferenceSectionFromDisplayedAnswer() {
        assertEquals("考试安排如下。", AnswerTextSanitizer.stripReferenceSection(
                "考试安排如下。\n\n参考来源:\n1. 知识库条目 \"考试安排\""
        ));
    }

    @Test
    void keepsOnlyCoreAnswerForSpeech() {
        assertEquals("考试安排如下。", AnswerTextSanitizer.forSpeech(
                "同学，你好！\n您所咨询的问题解答如下：考试安排如下。谢谢！\n\n"
                        + "以上答复含有AI解读成分，如有未尽事宜或其他问题，请具体咨询教务部联系人"
        ));
    }
}
