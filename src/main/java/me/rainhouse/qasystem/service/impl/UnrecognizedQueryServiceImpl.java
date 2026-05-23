package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.rainhouse.qasystem.entity.UnrecognizedQuery;
import me.rainhouse.qasystem.mapper.UnrecognizedQueryMapper;
import me.rainhouse.qasystem.service.UnrecognizedQueryService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UnrecognizedQueryServiceImpl extends ServiceImpl<UnrecognizedQueryMapper, UnrecognizedQuery> implements UnrecognizedQueryService {

    @Async
    @Override
    public void recordUnrecognized(Long userId, String queryText) {
        if (queryText == null || queryText.trim().isEmpty()) {
            return;
        }
        
        UnrecognizedQuery record = new UnrecognizedQuery();
        record.setUserId(userId);
        
        // 限制最大长度存储，防止恶意过长字符串 
        String safeQuery = queryText.length() > 500 ? queryText.substring(0, 500) : queryText;
        record.setQueryText(safeQuery);
        
        record.setStatus(0); // 默认: 0-待处理
        record.setCreatedAt(LocalDateTime.now());
        
        this.save(record);
    }

    @Override
    public void updateState(Long id, Integer status) {
        UnrecognizedQuery record = this.getById(id);
        if (record != null) {
            record.setStatus(status);
            this.updateById(record);
        }
    }
}