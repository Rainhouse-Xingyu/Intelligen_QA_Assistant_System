package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("survey_submission")
public class SurveySubmission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long surveyId;
    private Long userId;
    private LocalDateTime submitTime;
    private LocalDateTime createdAt;
}
