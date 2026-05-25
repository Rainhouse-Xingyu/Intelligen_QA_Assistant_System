package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.rainhouse.qasystem.entity.ChatMessage;
import me.rainhouse.qasystem.mapper.ChatMessageMapper;
import me.rainhouse.qasystem.service.ChatMessageService;
import org.springframework.stereotype.Service;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {
}