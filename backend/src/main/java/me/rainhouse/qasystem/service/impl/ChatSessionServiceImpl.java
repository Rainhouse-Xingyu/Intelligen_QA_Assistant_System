package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.rainhouse.qasystem.entity.ChatSession;
import me.rainhouse.qasystem.mapper.ChatSessionMapper;
import me.rainhouse.qasystem.service.ChatSessionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    @Override
    public ChatSession getOrCreateActiveSession(Long userId) {
        // 查询该用户最近30天内最新的一条会话且状态为0或1的（未结束）
        QueryWrapper<ChatSession> qw = new QueryWrapper<>();
        qw.eq("user_id", userId)
          .ne("status", 2)
          .ge("updated_at", LocalDateTime.now().minusDays(30))
          .orderByDesc("created_at")
          .last("LIMIT 1");

        ChatSession session = this.getOne(qw);
        if (session == null) {
            session = new ChatSession();
            session.setUserId(userId);
            session.setStatus(0); // AI托管
            session.setCreatedAt(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
            this.save(session);
        }
        return session;
    }
}
