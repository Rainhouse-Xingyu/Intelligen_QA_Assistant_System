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
    private String subject;
    private String description;
    private String purpose;
    private Integer status;
    private Long templateId;
    private String scopeType;
    private String scopeText;
    private Long publisherId;
    private Integer academicYear;
    private Integer termNo;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
