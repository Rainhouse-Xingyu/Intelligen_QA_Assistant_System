package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("academic_warning_record")
public class AcademicWarningRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long studentId;
    private String term;
    
    // 预警分析(大模型生成或规则生成)
    private String warningReason;
    
    // AI生成的帮扶方案
    private String aiSuggestedPlan;
    
    private String reportPdfUrl;
    private LocalDateTime createdAt;
}