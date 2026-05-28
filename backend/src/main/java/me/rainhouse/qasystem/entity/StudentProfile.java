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
    // 所需学分绩点
    private BigDecimal requiredGpa;
    private Integer failedCoursesCnt;
    private String psychologicalTag;
    
    // 风险等级: 0-无风险, 1-橙色预警, 2-红色预警
    private Integer riskLevel;
    
    private LocalDateTime updatedAt;
    
    // 素质教师姓名
    private String counselor;
}