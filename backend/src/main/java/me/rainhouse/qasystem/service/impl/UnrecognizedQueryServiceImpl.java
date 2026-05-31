package me.rainhouse.qasystem.service.impl;

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

        UnrecognizedQuery record = new UnrecognizedQuery();
        record.setQuestionText(queryText.trim());
        record.setModuleType(moduleType);
        record.setTopScore(topScore == null ? null : BigDecimal.valueOf(topScore).setScale(4, RoundingMode.HALF_UP));
        record.setFrequency(1);
        record.setStatus(0);
        record.setCreateTime(LocalDateTime.now());
        this.save(record);
    }

    @Override
    public void updateState(Long id, Integer status) {
        UnrecognizedQuery record = this.getById(id);
        if (record != null) {
            record.setStatus(status);
            record.setProcessTime(LocalDateTime.now());
            this.updateById(record);
        }
    }
}
