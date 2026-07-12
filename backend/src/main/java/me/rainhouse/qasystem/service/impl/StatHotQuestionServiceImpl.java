package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.rainhouse.qasystem.entity.HotQuestionConfig;
import me.rainhouse.qasystem.entity.KbQaEntry;
import me.rainhouse.qasystem.entity.QuestionHitRecord;
import me.rainhouse.qasystem.entity.StatHotQuestion;
import me.rainhouse.qasystem.mapper.HotQuestionConfigMapper;
import me.rainhouse.qasystem.mapper.KbQaEntryMapper;
import me.rainhouse.qasystem.mapper.QuestionHitRecordMapper;
import me.rainhouse.qasystem.mapper.StatHotQuestionMapper;
import me.rainhouse.qasystem.service.StatHotQuestionService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

@Service
public class StatHotQuestionServiceImpl extends ServiceImpl<StatHotQuestionMapper, StatHotQuestion> implements StatHotQuestionService {

    private final QuestionHitRecordMapper questionHitRecordMapper;
    private final KbQaEntryMapper kbQaEntryMapper;
    private final HotQuestionConfigMapper hotQuestionConfigMapper;

    public StatHotQuestionServiceImpl(QuestionHitRecordMapper questionHitRecordMapper,
                                      KbQaEntryMapper kbQaEntryMapper,
                                      HotQuestionConfigMapper hotQuestionConfigMapper) {
        this.questionHitRecordMapper = questionHitRecordMapper;
        this.kbQaEntryMapper = kbQaEntryMapper;
        this.hotQuestionConfigMapper = hotQuestionConfigMapper;
    }

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
            stat.setLastHitTime(LocalDateTime.now());
            stat.setStatDate(today);
            this.save(stat);
        } else {
            stat.setFrequency(stat.getFrequency() + 1);
            stat.setLastHitTime(LocalDateTime.now());
            this.updateById(stat);
        }
    }

    @Override
    public List<Map<String, Object>> getHotQuestions(int days, int limit) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        
        // 使用 MyBatis-Plus 的 listMaps 返回通用的键值对列表，方便前端 ECharts 组件绑定 "name" 和 "value"
        QueryWrapper<StatHotQuestion> qw = new QueryWrapper<>();
        qw.select("question_text as name",
                  "module_type as moduleType",
                  "max(answer_text) as answerText",
                  "sum(frequency) as value",
                  "max(last_hit_time) as lastHitTime")
          .ge("stat_date", startDate)
          .groupBy("question_text", "module_type")
          .orderByDesc("value")
          .last("LIMIT " + safeLimit(limit));
          
        return this.listMaps(qw);
    }

    @Override
    public List<Map<String, Object>> getSuggestedQuestionAnswers(int limit) {
        int safeLimit = safeLimit(limit);
        List<Map<String, Object>> rows = listLatestCommonQuestionEntries(safeLimit);
        if (rows.isEmpty()) {
            rows = listLatestStatQuestionAnswers(safeLimit);
        }

        Map<String, Map<String, Object>> uniqueRows = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String question = text(row.get("questionText"));
            String answer = text(row.get("answerText"));
            if (question == null || question.isBlank() || answer == null || answer.isBlank()) {
                continue;
            }
            uniqueRows.putIfAbsent(question.trim() + "\n" + answer.trim(), row);
        }

        return uniqueRows.values().stream()
                .limit(safeLimit)
                .toList();
    }

    @Override
    public List<Map<String, Object>> getRandomQuestionAnswers(int limit) {
        return getRandomQuestionAnswers(limit, null);
    }

    @Override
    public List<Map<String, Object>> getRandomQuestionAnswers(int limit, String moduleType) {
        int safeLimit = safeLimit(limit);
        List<Map<String, Object>> commonRows = randomQuestionAnswers(safeLimit, true, moduleType);
        if (commonRows.size() >= safeLimit) {
            return commonRows;
        }
        List<Map<String, Object>> fallbackRows = randomQuestionAnswers(safeLimit, false, moduleType);
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> row : commonRows) {
            merged.putIfAbsent(text(row.get("questionText")), row);
        }
        for (Map<String, Object> row : fallbackRows) {
            merged.putIfAbsent(text(row.get("questionText")), row);
            if (merged.size() >= safeLimit) {
                break;
            }
        }
        return merged.values().stream().limit(safeLimit).toList();
    }

    @Override
    public List<Map<String, Object>> getHomeHotQuestionAnswers(int limit) {
        int safeLimit = safeLimit(limit);
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (HotQuestionConfig config : activeHotQuestionConfigs(safeLimit)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", config.getId());
            row.put("questionText", config.getQuestionText());
            row.put("answerText", config.getAnswerText());
            row.put("moduleType", config.getModuleType());
            row.put("validUntil", config.getValidUntil());
            row.put("manual", true);
            merged.putIfAbsent(config.getQuestionText(), row);
        }
        if (merged.size() >= safeLimit) {
            return merged.values().stream().limit(safeLimit).toList();
        }
        for (Map<String, Object> row : listLatestStatQuestionAnswers(safeLimit)) {
            merged.putIfAbsent(text(row.get("questionText")), row);
            if (merged.size() >= safeLimit) {
                return merged.values().stream().limit(safeLimit).toList();
            }
        }
        for (Map<String, Object> row : getRandomQuestionAnswers(safeLimit)) {
            merged.putIfAbsent(text(row.get("questionText")), row);
            if (merged.size() >= safeLimit) {
                break;
            }
        }
        return merged.values().stream().limit(safeLimit).toList();
    }

    @Override
    public List<HotQuestionConfig> listHotQuestionConfigs() {
        return hotQuestionConfigMapper.selectList(new QueryWrapper<HotQuestionConfig>()
                .orderByAsc("sort_order")
                .orderByDesc("updated_at")
                .orderByDesc("created_at"));
    }

    @Override
    public HotQuestionConfig saveHotQuestionConfig(HotQuestionConfig config, Long userId) {
        if (config == null || !StringUtils.hasText(config.getQuestionText())) {
            throw new IllegalArgumentException("热门问题不能为空");
        }
        if (!StringUtils.hasText(config.getAnswerText())) {
            throw new IllegalArgumentException("热门问题答案不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        config.setQuestionText(config.getQuestionText().trim());
        config.setAnswerText(config.getAnswerText().trim());
        config.setModuleType(StringUtils.hasText(config.getModuleType()) ? config.getModuleType().trim() : null);
        config.setSortOrder(config.getSortOrder() == null ? 0 : config.getSortOrder());
        config.setEnabled(config.getEnabled() == null ? 1 : config.getEnabled());
        config.setUpdatedAt(now);
        if (config.getId() == null) {
            config.setCreatedBy(userId);
            config.setCreatedAt(now);
            hotQuestionConfigMapper.insert(config);
        } else {
            hotQuestionConfigMapper.updateById(config);
        }
        return config;
    }

    @Override
    public boolean deleteHotQuestionConfig(Long id) {
        return id != null && hotQuestionConfigMapper.deleteById(id) > 0;
    }

    private List<HotQuestionConfig> activeHotQuestionConfigs(int safeLimit) {
        return hotQuestionConfigMapper.selectList(new QueryWrapper<HotQuestionConfig>()
                .eq("enabled", 1)
                .and(wrapper -> wrapper.isNull("valid_until").or().ge("valid_until", LocalDateTime.now()))
                .orderByAsc("sort_order")
                .orderByDesc("updated_at")
                .last("LIMIT " + safeLimit));
    }

    private List<Map<String, Object>> randomQuestionAnswers(int safeLimit, boolean commonOnly, String moduleType) {
        QueryWrapper<KbQaEntry> qw = new QueryWrapper<>();
        qw.select("id",
                  "question as questionText",
                  "answer as answerText",
                  "module_type as moduleType",
                  "updated_at as lastHitTime")
          .eq("status", 1)
          .isNotNull("question")
          .ne("question", "")
          .isNotNull("answer")
          .ne("answer", "");
        if (commonOnly) {
            qw.eq("source_type", "common_question");
        }
        if (StringUtils.hasText(moduleType)) {
            List<String> terms = categoryTerms(moduleType);
            qw.and(wrapper -> {
                boolean first = true;
                for (String term : terms) {
                    if (!first) {
                        wrapper.or();
                    }
                    first = false;
                    wrapper.eq("module_type", term)
                            .or()
                            .eq("category_l1_name", term)
                            .or()
                            .eq("category_l2_name", term)
                            .or()
                            .eq("category_l3_name", term)
                            .or()
                            .apply("{0} LIKE CONCAT(module_type, '%')", term)
                            .or()
                            .apply("module_type LIKE CONCAT({0}, '%')", term)
                            .or()
                            .apply("{0} LIKE CONCAT(category_l1_name, '%')", term)
                            .or()
                            .apply("category_l1_name LIKE CONCAT({0}, '%')", term);
                }
            });
        }
        qw.last("ORDER BY RAND() LIMIT " + safeLimit);

        return kbQaEntryMapper.selectMaps(qw);
    }

    private List<Map<String, Object>> listLatestStatQuestionAnswers(int limit) {
        QueryWrapper<StatHotQuestion> qw = new QueryWrapper<>();
        qw.select("id",
                  "question_text as questionText",
                  "answer_text as answerText",
                  "module_type as moduleType",
                  "frequency as value",
                  "last_hit_time as lastHitTime")
          .isNotNull("answer_text")
          .ne("answer_text", "")
          .orderByDesc("id")
          .last("LIMIT " + limit);

        return this.listMaps(qw);
    }

    private List<Map<String, Object>> listLatestCommonQuestionEntries(int limit) {
        QueryWrapper<KbQaEntry> qw = new QueryWrapper<>();
        qw.select("id",
                  "question as questionText",
                  "answer as answerText",
                  "module_type as moduleType",
                  "updated_at as lastHitTime")
          .eq("source_type", "common_question")
          .eq("status", 1)
          .isNotNull("answer")
          .ne("answer", "")
          .orderByDesc("id")
          .last("LIMIT " + limit);

        return kbQaEntryMapper.selectMaps(qw);
    }

    @Override
    public int rebuildHotQuestions(LocalDate statDate) {
        LocalDate targetDate = statDate == null ? LocalDate.now() : statDate;
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.plusDays(1).atStartOfDay();

        QueryWrapper<QuestionHitRecord> hitQw = new QueryWrapper<>();
        hitQw.select("rewrite_question as question_text",
                        "module_type",
                        "knowledge_id",
                        "count(*) as frequency",
                        "max(created_at) as last_hit_time")
                .ge("created_at", start)
                .lt("created_at", end)
                .isNotNull("rewrite_question")
                .ne("rewrite_question", "")
                .groupBy("rewrite_question", "module_type", "knowledge_id")
                .orderByDesc("frequency");

        List<Map<String, Object>> rows = questionHitRecordMapper.selectMaps(hitQw);

        this.remove(new QueryWrapper<StatHotQuestion>().eq("stat_date", targetDate));
        if (rows.isEmpty()) {
            return 0;
        }

        List<StatHotQuestion> stats = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String questionText = text(row.get("question_text"));
            if (questionText == null || questionText.isBlank()) {
                continue;
            }
            Long knowledgeId = longValue(row.get("knowledge_id"));
            KbQaEntry entry = knowledgeId == null ? null : kbQaEntryMapper.selectById(knowledgeId);

            StatHotQuestion stat = new StatHotQuestion();
            stat.setQuestionText(questionText.length() > 500 ? questionText.substring(0, 500) : questionText);
            stat.setAnswerText(entry == null ? null : entry.getAnswer());
            stat.setModuleType(text(row.get("module_type")));
            stat.setFrequency(intValue(row.get("frequency")));
            stat.setLastHitTime(dateTimeValue(row.get("last_hit_time")));
            stat.setStatDate(targetDate);
            stats.add(stat);
        }

        if (stats.isEmpty()) {
            return 0;
        }
        this.saveBatch(stats);
        return stats.size();
    }

    private int safeLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private List<String> categoryTerms(String moduleType) {
        String term = moduleType == null ? "" : moduleType.trim();
        if (term.isEmpty()) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        terms.add(term);
        if (term.contains("考务") || term.contains("考试")) {
            terms.add("考务通知");
            terms.add("考务资料");
            terms.add("考务");
        }
        if (term.contains("教学")) {
            terms.add("教学运行");
            terms.add("教学帮扶");
            terms.add("教学");
        }
        if (term.contains("学业")) {
            terms.add("学业帮扶");
            terms.add("教学帮扶");
            terms.add("学业");
        }
        if (term.contains("心理")) {
            terms.add("心理辅导");
            terms.add("心理指导");
            terms.add("心理");
        }
        return terms.stream().filter(StringUtils::hasText).toList();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : Long.valueOf(text);
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private LocalDateTime dateTimeValue(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
    }
}
