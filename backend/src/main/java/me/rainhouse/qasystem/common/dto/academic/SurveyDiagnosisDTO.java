package me.rainhouse.qasystem.common.dto.academic;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SurveyDiagnosisDTO {
    private Long submissionId;
    private Long surveyId;
    private String surveyTitle;
    private Long studentId;
    private String username;
    private String realName;
    private String className;
    private LocalDateTime submitTime;
    private String warningLevel;
    private String reportWarningLevel;
    private String reportWarningReason;
    private String reportWeaknessItems;
    private String reportHelpMeasures;
    private String reportCounselor;
    private String reportContactPhone;
    private String reportRemark;
    private String surveyWarningLevel;
    private String finalWarningLevel;
    private String comparisonResult;
    private String comparisonDetail;
    private String primaryProblem;
    private String secondaryProblem;
    private String summary;
    private String helpPlan;
    private String questionnaireWeaknessItems;
    private String improvementGoal;
    private String supportCycle;
    private String evaluationMethod;
    private String reportConclusion;
    private List<DimensionReport> dimensions = new ArrayList<>();
    private List<EvidenceAnswer> evidences = new ArrayList<>();

    @Data
    public static class DimensionReport {
        private String dimension;
        private String label;
        private Double score;
        private String status;
        private Integer answerCount;
        private String reason;
    }

    @Data
    public static class EvidenceAnswer {
        private Long questionId;
        private String dimension;
        private String questionText;
        private String answerText;
        private Integer riskScore;
        private String riskPoint;
    }
}
