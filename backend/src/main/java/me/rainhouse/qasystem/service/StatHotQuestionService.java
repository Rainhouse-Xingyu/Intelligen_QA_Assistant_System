package me.rainhouse.qasystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.rainhouse.qasystem.entity.StatHotQuestion;

import java.util.List;
import java.util.Map;

public interface StatHotQuestionService extends IService<StatHotQuestion> {
    
    /**
     * 【4.1模块】埋点统计：记录一次用户提问
     * @param question 用户提问文本
     */
    void recordQuestion(String question);

    /**
     * 【4.1模块】获取常见问题统计，供 ECharts 词云或折线图展示
     * @param days 统计最近几天的数据
     * @param limit 返回最多多少条
     * @return 包含 name 和 value 的 Map 列表
     */
    List<Map<String, Object>> getHotQuestions(int days, int limit);

    /**
     * 获取可在学生端直接展示答案的常见问题。
     *
     * @param limit 返回最多多少条
     * @return 包含 questionText、answerText、moduleType 的常见问题列表
     */
    List<Map<String, Object>> getSuggestedQuestionAnswers(int limit);

    /**
     * 重建指定日期的常见问题统计。
     *
     * @param statDate 统计日期
     * @return 写入的常见问题统计条数
     */
    int rebuildHotQuestions(java.time.LocalDate statDate);
}
