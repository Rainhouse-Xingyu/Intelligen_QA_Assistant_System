package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生成长档案表
 */
@Data
@TableName("student_growth_archive")
public class StudentGrowthArchive {

    /**
     * 主键，自动递增
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 学校真实学号（供老师在管理后台检索筛选）
     */
    private String realStudentId;

    /**
     * 传给 Coze 的假学号（用于审计对账和云端标识）
     */
    private String fakeStudentId;

    /**
     * 对应的预警级别
     */
    private String warningLevel;

    /**
     * 调查问卷暴露的各项指标变化
     */
    private String surveyIndicator;

    /**
     * Coze 大模型生成的个性化一对一帮扶方案
     */
    private String helpPlan;

    /**
     * 记录生成时间，方便以学期为单位导出成效报告
     */
    private LocalDateTime createTime;
}
