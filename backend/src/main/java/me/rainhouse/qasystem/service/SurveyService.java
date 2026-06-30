package me.rainhouse.qasystem.service;

import me.rainhouse.qasystem.common.dto.survey.SurveyDetailDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveySubmissionDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveySubmitRequest;
import me.rainhouse.qasystem.entity.Survey;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SurveyService {
    Survey importTemplate(MultipartFile file, String title, String description, Long adminId);

    List<Survey> listAdminSurveys();

    SurveyDetailDTO getDetail(Long surveyId, Long userId);

    Survey publish(Long surveyId);

    Survey close(Long surveyId);

    List<SurveyDetailDTO> listStudentSurveys(Long userId);

    void submit(Long surveyId, Long userId, SurveySubmitRequest request);

    List<SurveySubmissionDTO> listSubmissions(Long surveyId);
}
