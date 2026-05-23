package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("unrecognized_query")
public class UnrecognizedQuery {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String queryText;
    
    // 处理状态: 0-待处理, 1-已入知识库, 2-忽略
    private Integer status; 
    
    private LocalDateTime createdAt;
}