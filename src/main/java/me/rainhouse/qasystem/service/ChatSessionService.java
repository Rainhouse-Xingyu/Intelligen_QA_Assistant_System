package me.rainhouse.qasystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.rainhouse.qasystem.entity.ChatSession;

public interface ChatSessionService extends IService<ChatSession> {
    
    /**
     * 获取或创建活跃的会话（当会话未被关闭时复用）
     */
    ChatSession getOrCreateActiveSession(Long userId);
}