package me.rainhouse.qasystem.common.dto.survey;

import lombok.Data;

import java.util.List;

@Data
public class SurveySubmitRequest {
    private List<SurveyAnswerRequest> answers;
}
