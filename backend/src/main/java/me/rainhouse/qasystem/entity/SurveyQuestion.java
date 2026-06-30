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
    private Integer questionNo;
    private String questionText;
    private Integer questionType; // 1-量表题, 2-文本题
    private Integer required; // 0-选填, 1-必填
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
