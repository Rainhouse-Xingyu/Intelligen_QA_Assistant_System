package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("student_profile")
public class StudentProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    
    // 脱敏ID(传给AI时使用代替真实学号，保护隐私)
    private String maskingId;
    
    private BigDecimal gpa;
    private Integer failedCoursesCnt;
    private String psychologicalTag;
    
    // 风险等级: 0-无风险, 1-低, 2-中, 3-高
    private Integer riskLevel;
    
    private LocalDateTime updatedAt;
}