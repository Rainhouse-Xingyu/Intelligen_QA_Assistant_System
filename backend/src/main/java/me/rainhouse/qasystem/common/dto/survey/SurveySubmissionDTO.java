package me.rainhouse.qasystem.common.dto.survey;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SurveySubmissionDTO {
    private Long id;
    private Long surveyId;
    private Long userId;
    private String username;
    private String realName;
    private LocalDateTime submitTime;
    private List<SurveySubmissionAnswerDTO> answers;
}
