package me.rainhouse.qasystem.common.dto.survey;

import lombok.Data;

@Data
public class SurveyAnswerRequest {
    private Long questionId;
    private Integer numericAnswer;
    private String textAnswer;
}
