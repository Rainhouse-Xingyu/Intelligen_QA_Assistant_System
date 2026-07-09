package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("survey_question")
public class SurveyQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long surveyId;
    private Long templateQuestionId;
    private Integer questionNo;
    private String questionCode;
    private String indicatorName;
    private String questionText;
    private Integer questionType;
    private Integer required;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
