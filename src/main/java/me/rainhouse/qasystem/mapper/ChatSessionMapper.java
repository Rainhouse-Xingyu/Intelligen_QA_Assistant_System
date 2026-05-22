package me.rainhouse.qasystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.rainhouse.qasystem.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}