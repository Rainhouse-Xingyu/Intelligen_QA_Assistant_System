package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.rainhouse.qasystem.entity.StatHotQuestion;
import me.rainhouse.qasystem.mapper.StatHotQuestionMapper;
import me.rainhouse.qasystem.service.StatHotQuestionService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class StatHotQuestionServiceImpl extends ServiceImpl<StatHotQuestionMapper, StatHotQuestion> implements StatHotQuestionService {

    @Async
    @Override
    public void recordQuestion(String question) {
        if (question == null || question.trim().isEmpty()) {
            return;
        }
        
        // 简单清洗逻辑：实际商业项目中这里会接 NLP 分词器（结巴分词）剥离停用词。
        // 这里作为通用演示，我们将原文本首尾去空格，并截取在合理长度内直接入库打点
        String cleanQ = question.trim();
        if (cleanQ.length() > 500) {
            cleanQ = cleanQ.substring(0, 500);
        }

        LocalDate today = LocalDate.now();
        QueryWrapper<StatHotQuestion> qw = new QueryWrapper<>();
        qw.eq("question_text", cleanQ).eq("stat_date", today);

        StatHotQuestion stat = this.getOne(qw);
        if (stat == null) {
            stat = new StatHotQuestion();
            stat.setQuestionText(cleanQ);
            stat.setFrequency(1);
            stat.setStatDate(today);
            this.save(stat);
        } else {
            stat.setFrequency(stat.getFrequency() + 1);
            this.updateById(stat);
        }
    }

    @Override
    public List<Map<String, Object>> getHotQuestions(int days, int limit) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        
        // 使用 MyBatis-Plus 的 listMaps 返回通用的键值对列表，方便前端 ECharts 组件绑定 "name" 和 "value"
        QueryWrapper<StatHotQuestion> qw = new QueryWrapper<>();
        qw.select("question_text as name", "sum(frequency) as value")
          .ge("stat_date", startDate)
          .groupBy("question_text")
          .orderByDesc("value")
          .last("LIMIT " + limit);
          
        return this.listMaps(qw);
    }
}