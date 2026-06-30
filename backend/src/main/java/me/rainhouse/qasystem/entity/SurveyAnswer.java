package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("survey_answer")
public class SurveyAnswer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long submissionId;
    private Long surveyId;
    private Long questionId;
    private Long userId;
    private Integer numericAnswer;
    private String textAnswer;
    private LocalDateTime createdAt;
}
