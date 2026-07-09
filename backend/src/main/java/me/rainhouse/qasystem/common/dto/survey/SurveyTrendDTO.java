package me.rainhouse.qasystem.common.dto.survey;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SurveyTrendDTO {
    private Long studentId;
    private String username;
    private String realName;
    private List<Series> series = new ArrayList<>();

    @Data
    public static class Series {
        private String questionCode;
        private String indicatorName;
        private String questionText;
        private List<Point> points = new ArrayList<>();
    }

    @Data
    public static class Point {
        private Long surveyId;
        private String surveyTitle;
        private Integer academicYear;
        private Integer termNo;
        private LocalDateTime submitTime;
        private Integer value;
    }
}
