package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("survey")
public class Survey {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private Integer status; // 0-草稿, 1-已发布, 2-已关闭
    private String scopeType; // ALL-全校
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
}
