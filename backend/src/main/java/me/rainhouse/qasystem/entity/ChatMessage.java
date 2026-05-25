package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Integer senderType; // 1-用户, 2-AI, 3-人工客服
    private Integer msgType; // 1-文本, 2-语音
    private String content;
    private String mediaUrl;
    private String intentTag;
    private LocalDateTime createdAt;
}