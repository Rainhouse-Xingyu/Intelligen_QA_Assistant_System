package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.rainhouse.qasystem.entity.UnrecognizedQuery;
import me.rainhouse.qasystem.mapper.UnrecognizedQueryMapper;
import me.rainhouse.qasystem.service.UnrecognizedQueryService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class UnrecognizedQueryServiceImpl extends ServiceImpl<UnrecognizedQueryMapper, UnrecognizedQuery> implements UnrecognizedQueryService {

    @Async
    @Override
    public void recordUnrecognized(Long userId, String queryText) {
        recordUnrecognized(userId, queryText, null, null);
    }

    @Async
    @Override
    public void recordUnrecognized(Long userId, String queryText, String moduleType, Double topScore) {
        if (queryText == null || queryText.trim().isEmpty()) {
            return;
        }

        String cleanQuestion = queryText.trim();
        BigDecimal score = topScore == null ? null : BigDecimal.valueOf(topScore).setScale(4, RoundingMode.HALF_UP);
        QueryWrapper<UnrecognizedQuery> qw = new QueryWrapper<>();
        qw.eq("question_text", cleanQuestion)
                .eq("status", 0);
        if (moduleType == null || moduleType.trim().isEmpty()) {
            qw.isNull("module_type");
        } else {
            qw.eq("module_type", moduleType.trim());
        }

        UnrecognizedQuery existing = this.getOne(qw, false);
        if (existing != null) {
            existing.setFrequency((existing.getFrequency() == null ? 1 : existing.getFrequency()) + 1);
            if (score != null && (existing.getTopScore() == null || score.compareTo(existing.getTopScore()) > 0)) {
                existing.setTopScore(score);
            }
            this.updateById(existing);
            return;
        }

        UnrecognizedQuery record = new UnrecognizedQuery();
        record.setQuestionText(cleanQuestion);
        record.setModuleType(moduleType == null ? null : moduleType.trim());
        record.setTopScore(score);
        record.setFrequency(1);
        record.setStatus(0);
        record.setCreateTime(LocalDateTime.now());
        this.save(record);
    }

    @Override
    public void updateState(Long id, Integer status) {
        updateState(id, status, null);
    }

    @Override
    public void updateState(Long id, Integer status, Long processUser) {
        UnrecognizedQuery record = this.getById(id);
        if (record != null) {
            record.setStatus(status);
            record.setProcessUser(processUser);
            record.setProcessTime(LocalDateTime.now());
            this.updateById(record);
        }
    }
}
