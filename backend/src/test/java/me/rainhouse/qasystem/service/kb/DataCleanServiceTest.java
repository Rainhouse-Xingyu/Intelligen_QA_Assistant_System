package me.rainhouse.qasystem.service.kb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataCleanServiceTest {

    private final DataCleanService dataCleanService = new DataCleanService();

    @Test
    void splitsContinuousPolicyTextByArticleMarkers() {
        String text = """
                第一条 补考对象 凡课程考核不合格的学生，可按学校规定参加补考。
                第二条 报名时间 补考报名应在教务系统通知的时间范围内完成，逾期不再受理。
                第三条 考试安排 补考安排以教务处发布的考试通知为准，学生应提前查询考场。
                """;

        List<FaqItem> items = dataCleanService.cleanToFaq(text, "补考管理办法.docx");

        assertEquals(3, items.size());
        assertTrue(items.get(0).question().contains("补考对象"));
        assertTrue(items.get(1).answer().contains("报名时间"));
        assertTrue(items.get(2).answer().contains("考试安排"));
    }

    @Test
    void splitsInlineNumberedPolicyTextWithoutBlankLines() {
        String text = "一、申请条件 学生因病不能参加考试，应提交证明材料。二、办理流程 学生需在系统发起申请并等待学院审核。三、注意事项 逾期申请原则上不予受理。";

        List<FaqItem> items = dataCleanService.cleanToFaq(text, "缓考申请说明.txt");

        assertEquals(3, items.size());
        assertTrue(items.stream().anyMatch(item -> item.question().contains("申请条件")));
        assertTrue(items.stream().anyMatch(item -> item.question().contains("办理流程")));
        assertTrue(items.stream().anyMatch(item -> item.question().contains("注意事项")));
    }

    @Test
    void splitsInlineQaPairsWithoutMergingSeveralQuestionsIntoOneAnswer() {
        String text = """
                问题：如何申请补考？ 答案：学生需在教务系统提交补考申请。
                问题：补考什么时候报名？ 答案：报名时间以教务处通知为准。
                """;

        List<FaqItem> items = dataCleanService.cleanToFaq(text, "补考FAQ.txt");

        assertEquals(2, items.size());
        assertEquals("如何申请补考？", items.get(0).question());
        assertEquals("学生需在教务系统提交补考申请。", items.get(0).answer());
        assertEquals("补考什么时候报名？", items.get(1).question());
        assertEquals("报名时间以教务处通知为准。", items.get(1).answer());
    }

    @Test
    void buildsMatchedQuestionsForDecimalPolicyItems() {
        String text = """
                3.1专业确定校内外指导教师，安排必要的毕业设计（论文）指导教师、学生培训和专题讲座，明确职责与要求。 4.
                3.2指导教师拟题、实施毕业设计（论文）题目双选，专业、二级学院逐级审查确认后指导教师下达毕业设计（论文）任务书。 4.
                3.3毕业设计（论文）进行过程中，各二级学院按要求进行前、中、后三阶段检查，学校不定期组织抽查。 4.
                """;

        List<FaqItem> items = dataCleanService.cleanToFaq(text, "考务通知.docx");

        assertEquals(3, items.size());
        assertEquals("校内外指导教师确定和培训安排是如何规定的？", items.get(0).question());
        assertEquals("指导教师拟题和任务书下达是如何规定的？", items.get(1).question());
        assertEquals("毕业设计（论文）过程检查是如何规定的？", items.get(2).question());
        assertTrue(items.stream().noneMatch(item -> item.question().contains("适用条件")));
        assertTrue(items.stream().noneMatch(item -> item.answer().endsWith("4.")));
    }
}
