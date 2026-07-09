package me.rainhouse.qasystem.service;

import me.rainhouse.qasystem.common.dto.survey.SurveyDetailDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveyTaskRequest;
import me.rainhouse.qasystem.common.dto.survey.SurveySubmissionDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveySubmitRequest;
import me.rainhouse.qasystem.common.dto.survey.SurveyTrendDTO;
import me.rainhouse.qasystem.entity.Survey;
import me.rainhouse.qasystem.entity.SurveyTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SurveyService {
    Survey importTemplate(MultipartFile file, String title, String description, Long adminId);

    SurveyTemplate importSurveyTemplate(MultipartFile file, String name, String description, Long userId);

    List<SurveyTemplate> listTemplates();

    Survey createTask(SurveyTaskRequest request, Long userId);

    List<Survey> listAdminSurveys();

    SurveyDetailDTO getDetail(Long surveyId, Long userId);

    Survey publish(Long surveyId);

    Survey close(Long surveyId);

    void deleteSurvey(Long surveyId);

    void deleteSubmission(Long submissionId);

    List<SurveyDetailDTO> listStudentSurveys(Long userId);

    void submit(Long surveyId, Long userId, SurveySubmitRequest request);

    List<SurveySubmissionDTO> listSubmissions(Long surveyId);

    byte[] exportSubmissionsExcel(Long surveyId);

    SurveyTrendDTO getStudentTrend(Long userId);
}
