package me.rainhouse.qasystem.controller;

import me.rainhouse.qasystem.common.dto.survey.SurveyDetailDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveySubmissionDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveySubmitRequest;
import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.entity.Survey;
import me.rainhouse.qasystem.service.SurveyService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/survey")
public class SurveyController {

    private final SurveyService surveyService;

    public SurveyController(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    @PostMapping("/admin/import")
    public Result<Survey> importTemplate(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "title", required = false) String title,
                                         @RequestParam(value = "description", required = false) String description,
                                         HttpServletRequest request) {
        try {
            requireAdmin(request);
            Long userId = getUserId(request);
            return Result.success(surveyService.importTemplate(file, title, description, userId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/admin/list")
    public Result<List<Survey>> listAdminSurveys(HttpServletRequest request) {
        try {
            requireAdmin(request);
            return Result.success(surveyService.listAdminSurveys());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/admin/{surveyId}")
    public Result<SurveyDetailDTO> adminDetail(@PathVariable Long surveyId,
                                               HttpServletRequest request) {
        try {
            requireAdmin(request);
            return Result.success(surveyService.getDetail(surveyId, null));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/admin/{surveyId}/publish")
    public Result<Survey> publish(@PathVariable Long surveyId,
                                  HttpServletRequest request) {
        try {
            requireAdmin(request);
            return Result.success(surveyService.publish(surveyId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/admin/{surveyId}/close")
    public Result<Survey> close(@PathVariable Long surveyId,
                                HttpServletRequest request) {
        try {
            requireAdmin(request);
            return Result.success(surveyService.close(surveyId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/admin/{surveyId}/submissions")
    public Result<List<SurveySubmissionDTO>> listSubmissions(@PathVariable Long surveyId,
                                                             HttpServletRequest request) {
        try {
            requireAdmin(request);
            return Result.success(surveyService.listSubmissions(surveyId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/student/list")
    public Result<List<SurveyDetailDTO>> listStudentSurveys(HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            return Result.success(surveyService.listStudentSurveys(userId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/student/{surveyId}")
    public Result<SurveyDetailDTO> studentDetail(@PathVariable Long surveyId,
                                                 HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            return Result.success(surveyService.getDetail(surveyId, userId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/student/{surveyId}/submit")
    public Result<String> submit(@PathVariable Long surveyId,
                                 @RequestBody SurveySubmitRequest submitRequest,
                                 HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            surveyService.submit(surveyId, userId, submitRequest);
            return Result.success("提交成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    private Long getUserId(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj == null) {
            throw new IllegalStateException("未获取到登录用户");
        }
        return Long.valueOf(userIdObj.toString());
    }

    private void requireAdmin(HttpServletRequest request) {
        Object roleObj = request.getAttribute("role");
        Integer role = roleObj == null ? null : Integer.valueOf(roleObj.toString());
        if (!Integer.valueOf(3).equals(role)) {
            throw new IllegalStateException("仅管理员可操作");
        }
    }
}
