package me.rainhouse.qasystem.common.dto.survey;

import lombok.Data;

@Data
public class SurveySubmissionAnswerDTO {
    private Long questionId;
    private Integer questionNo;
    private String questionText;
    private Integer questionType;
    private Integer numericAnswer;
    private String textAnswer;
}
