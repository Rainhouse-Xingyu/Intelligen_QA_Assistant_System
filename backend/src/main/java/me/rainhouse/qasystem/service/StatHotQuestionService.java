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
     * 【4.1模块】获取热点问题统计，供 ECharts 词云或折线图展示
     * @param days 统计最近几天的数据
     * @param limit 返回最多多少条
     * @return 包含 name 和 value 的 Map 列表
     */
    List<Map<String, Object>> getHotQuestions(int days, int limit);

    /**
     * 重建指定日期的热点问题统计。
     *
     * @param statDate 统计日期
     * @return 写入的热点统计条数
     */
    int rebuildHotQuestions(java.time.LocalDate statDate);
}
