package me.rainhouse.qasystem.common.dto.survey;

import lombok.Data;

@Data
public class SurveyQuestionDTO {
    private Long id;
    private Integer questionNo;
    private String questionText;
    private Integer questionType;
    private Integer required;
    private Integer sortOrder;
}
