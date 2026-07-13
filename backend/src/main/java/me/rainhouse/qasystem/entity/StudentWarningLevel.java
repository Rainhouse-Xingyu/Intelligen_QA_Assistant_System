package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("student_warning_level")
public class StudentWarningLevel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String studentNo;
    private String className;
    private String studentName;
    private String warningLevel;
    private String warningReason;
    private String weaknessItems;
    private String helpMeasures;
    private String counselor;
    private String contactPhone;
    private String remark;
    private LocalDateTime createdAt;
}
