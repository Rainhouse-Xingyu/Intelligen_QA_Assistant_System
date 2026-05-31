package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import me.rainhouse.qasystem.entity.QuestionHitRecord;
import me.rainhouse.qasystem.entity.UnrecognizedQuery;
import me.rainhouse.qasystem.mapper.QuestionHitRecordMapper;
import me.rainhouse.qasystem.mapper.UnrecognizedQueryMapper;
import me.rainhouse.qasystem.service.DataStatService;
import me.rainhouse.qasystem.service.StatHotQuestionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataStatServiceImpl implements DataStatService {

    private final StatHotQuestionService statHotQuestionService;
    private final QuestionHitRecordMapper questionHitRecordMapper;
    private final UnrecognizedQueryMapper unrecognizedQueryMapper;

    public DataStatServiceImpl(StatHotQuestionService statHotQuestionService,
                               QuestionHitRecordMapper questionHitRecordMapper,
                               UnrecognizedQueryMapper unrecognizedQueryMapper) {
        this.statHotQuestionService = statHotQuestionService;
        this.questionHitRecordMapper = questionHitRecordMapper;
        this.unrecognizedQueryMapper = unrecognizedQueryMapper;
    }

    @Scheduled(cron = "${stat.hot-question.cron:0 0/30 * * * ?}")
    public void scheduledRefreshTodayHotQuestions() {
        rebuildHotQuestions(LocalDate.now());
    }

    @Override
    public int rebuildHotQuestions(LocalDate statDate) {
        return statHotQuestionService.rebuildHotQuestions(statDate);
    }

    @Override
    public int refreshRecentHotQuestions(int days) {
        int safeDays = Math.max(1, Math.min(days, 30));
        int total = 0;
        LocalDate today = LocalDate.now();
        for (int i = 0; i < safeDays; i++) {
            total += rebuildHotQuestions(today.minusDays(i));
        }
        return total;
    }

    @Override
    public Map<String, Object> getFallbackOverview(int days) {
        LocalDateTime start = LocalDate.now().minusDays(Math.max(1, days)).atStartOfDay();
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("days", Math.max(1, days));
        overview.put("noHitCount", hitCount(0, start));
        overview.put("weakHitCount", hitCount(1, start));
        overview.put("strongHitCount", hitCount(2, start));
        overview.put("unrecognizedTotal", unrecognizedCount(null, start));
        overview.put("unrecognizedPending", unrecognizedCount(0, start));
        overview.put("topUnrecognized", topUnrecognized(start, 20));
        return overview;
    }

    private Long hitCount(Integer hitStatus, LocalDateTime start) {
        QueryWrapper<QuestionHitRecord> qw = new QueryWrapper<>();
        qw.eq("hit_status", hitStatus)
                .ge("created_at", start);
        return questionHitRecordMapper.selectCount(qw);
    }

    private Long unrecognizedCount(Integer status, LocalDateTime start) {
        QueryWrapper<UnrecognizedQuery> qw = new QueryWrapper<>();
        qw.ge("create_time", start);
        if (status != null) {
            qw.eq("status", status);
        }
        return unrecognizedQueryMapper.selectCount(qw);
    }

    private List<Map<String, Object>> topUnrecognized(LocalDateTime start, int limit) {
        QueryWrapper<UnrecognizedQuery> qw = new QueryWrapper<>();
        qw.select("question_text as questionText",
                        "module_type as moduleType",
                        "max(top_score) as topScore",
                        "sum(frequency) as frequency",
                        "min(create_time) as firstSeenTime",
                        "max(create_time) as lastSeenTime")
                .ge("create_time", start)
                .eq("status", 0)
                .groupBy("question_text", "module_type")
                .orderByDesc("frequency")
                .last("LIMIT " + Math.max(1, Math.min(limit, 100)));
        return unrecognizedQueryMapper.selectMaps(qw);
    }
}
