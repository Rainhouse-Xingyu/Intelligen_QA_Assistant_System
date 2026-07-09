package me.rainhouse.qasystem.common.dto.survey;

import lombok.Data;

@Data
public class SurveyQuestionDTO {
    private Long id;
    private Integer questionNo;
    private String questionCode;
    private String indicatorName;
    private String questionText;
    private Integer questionType;
    private Integer required;
    private Integer sortOrder;
}
