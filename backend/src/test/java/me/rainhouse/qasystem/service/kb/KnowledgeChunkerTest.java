package me.rainhouse.qasystem.service.kb;

import me.rainhouse.qasystem.entity.KbQaEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeChunkerTest {

    private final KnowledgeChunker knowledgeChunker = new KnowledgeChunker();

    @Test
    void splitsLongAnswerIntoFineGrainedChunks() {
        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            answer.append("第").append(i + 1).append("项要求学生按照教务系统通知完成材料提交和审核确认。");
        }

        List<KbQaEntry> entries = knowledgeChunker.chunk(
                List.of(new FaqItem("补考办理要求是什么？", answer.toString())),
                1L,
                "tester",
                "考务通知",
                "Word"
        );

        assertTrue(entries.size() > 1);
        assertTrue(entries.stream().allMatch(entry -> entry.getAnswer().length() <= 900));
    }

    @Test
    void keepsOneEntryPerQuestionForPolicyItems() {
        List<FaqItem> items = List.of(
                new FaqItem("补考对象的适用条件是什么？", "凡课程考核不合格的学生，可按学校规定参加补考。"),
                new FaqItem("报名时间的时间要求是什么？", "补考报名应在教务系统通知的时间范围内完成，逾期不再受理。"),
                new FaqItem("考试安排的时间要求是什么？", "补考安排以教务处发布的考试通知为准，学生应提前查询考场。"),
                new FaqItem("注意事项政策是如何规定的？", "学生应携带有效证件参加考试，并遵守考场纪律。")
        );

        List<KbQaEntry> entries = knowledgeChunker.chunk(items, 2L, "tester", "考务通知", "Word");

        assertEquals(items.size(), entries.size());
        assertTrue(entries.stream().allMatch(entry -> "考务通知".equals(entry.getModuleType())));
        assertFalse(entries.stream().anyMatch(entry -> entry.getQuestion().startsWith("政策文件第")));
        assertTrue(entries.stream().anyMatch(entry -> "补考对象的适用条件是什么？".equals(entry.getQuestion())));
        assertTrue(entries.stream().anyMatch(entry -> "报名时间的时间要求是什么？".equals(entry.getQuestion())));
        assertTrue(entries.stream().allMatch(entry -> entry.getAnswer().length() <= 900));
    }
}
