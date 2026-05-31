package me.rainhouse.qasystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.rainhouse.qasystem.entity.UnrecognizedQuery;

public interface UnrecognizedQueryService extends IService<UnrecognizedQuery> {
    
    /**
     * 【4.3模块】记录未识别的/兜底提问
     * @param userId 提问用户ID
     * @param queryText 原始问题文本
     */
    void recordUnrecognized(Long userId, String queryText);

    void recordUnrecognized(Long userId, String queryText, String moduleType, Double topScore);

    /**
     * 【4.3模块】更新处理状态
     * @param id 记录的主键
     * @param status 状态 (1-已入库 2-忽略)
     */
    void updateState(Long id, Integer status);
}
