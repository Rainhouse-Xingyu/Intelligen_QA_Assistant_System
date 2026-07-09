package me.rainhouse.qasystem.common.dto.survey;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SurveyTaskRequest {
    private Long templateId;
    private String title;
    private String subject;
    private String description;
    private String purpose;
    private String scopeType;
    private String scopeText;
    private Integer academicYear;
    private Integer termNo;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean publishNow;
}
