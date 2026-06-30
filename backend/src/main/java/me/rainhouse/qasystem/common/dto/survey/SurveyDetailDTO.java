package me.rainhouse.qasystem.common.dto.survey;

import lombok.Data;
import me.rainhouse.qasystem.entity.Survey;

import java.util.List;

@Data
public class SurveyDetailDTO {
    private Survey survey;
    private Boolean submitted;
    private Long submissionId;
    private List<SurveyQuestionDTO> questions;
}
