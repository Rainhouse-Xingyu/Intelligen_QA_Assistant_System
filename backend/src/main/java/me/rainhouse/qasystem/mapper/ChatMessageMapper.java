package me.rainhouse.qasystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.rainhouse.qasystem.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}