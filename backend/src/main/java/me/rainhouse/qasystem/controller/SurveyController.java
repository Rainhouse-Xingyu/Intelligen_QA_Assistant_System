package me.rainhouse.qasystem.controller;

import me.rainhouse.qasystem.common.dto.survey.SurveyDetailDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveySubmissionDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveySubmitRequest;
import me.rainhouse.qasystem.common.dto.survey.SurveyTaskRequest;
import me.rainhouse.qasystem.common.dto.survey.SurveyTrendDTO;
import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.entity.Survey;
import me.rainhouse.qasystem.entity.SurveyTemplate;
import me.rainhouse.qasystem.service.SurveyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
            requireSurveyManager(request);
            return Result.success(surveyService.importTemplate(file, title, description, getUserId(request)));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/admin/templates/import")
    public Result<SurveyTemplate> importSurveyTemplate(@RequestParam("file") MultipartFile file,
                                                       @RequestParam(value = "name", required = false) String name,
                                                       @RequestParam(value = "description", required = false) String description,
                                                       HttpServletRequest request) {
        try {
            requireSurveyManager(request);
            return Result.success(surveyService.importSurveyTemplate(file, name, description, getUserId(request)));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/admin/templates")
    public Result<List<SurveyTemplate>> listTemplates(HttpServletRequest request) {
        try {
            requireSurveyManager(request);
            return Result.success(surveyService.listTemplates());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/admin/tasks")
    public Result<Survey> createTask(@RequestBody SurveyTaskRequest taskRequest,
                                     HttpServletRequest request) {
        try {
            requireSurveyManager(request);
            return Result.success(surveyService.createTask(taskRequest, getUserId(request)));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/admin/list")
    public Result<List<Survey>> listAdminSurveys(HttpServletRequest request) {
        try {
            requireSurveyManager(request);
            return Result.success(surveyService.listAdminSurveys());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/admin/{surveyId}")
    public Result<SurveyDetailDTO> adminDetail(@PathVariable Long surveyId,
                                               HttpServletRequest request) {
        try {
            requireSurveyManager(request);
            return Result.success(surveyService.getDetail(surveyId, null));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/admin/{surveyId}/publish")
    public Result<Survey> publish(@PathVariable Long surveyId,
                                  HttpServletRequest request) {
        try {
            requireSurveyManager(request);
            return Result.success(surveyService.publish(surveyId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/admin/{surveyId}/close")
    public Result<Survey> close(@PathVariable Long surveyId,
                                HttpServletRequest request) {
        try {
            requireSurveyManager(request);
            return Result.success(surveyService.close(surveyId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/admin/{surveyId}")
    public Result<String> deleteSurvey(@PathVariable Long surveyId,
                                       HttpServletRequest request) {
        try {
            requireSurveyManager(request);
            surveyService.deleteSurvey(surveyId);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/admin/{surveyId}/submissions")
    public Result<List<SurveySubmissionDTO>> listSubmissions(@PathVariable Long surveyId,
                                                             HttpServletRequest request) {
        try {
            requireSurveyManager(request);
            return Result.success(surveyService.listSubmissions(surveyId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/admin/submissions/{submissionId}")
    public Result<String> deleteSubmission(@PathVariable Long submissionId,
                                           HttpServletRequest request) {
        try {
            requireSurveyManager(request);
            surveyService.deleteSubmission(submissionId);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/admin/students/{userId}/trends")
    public Result<SurveyTrendDTO> adminStudentTrend(@PathVariable Long userId,
                                                    HttpServletRequest request) {
        try {
            requireSurveyManager(request);
            return Result.success(surveyService.getStudentTrend(userId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/student/list")
    public Result<List<SurveyDetailDTO>> listStudentSurveys(HttpServletRequest request) {
        try {
            return Result.success(surveyService.listStudentSurveys(getUserId(request)));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/student/trends")
    public Result<SurveyTrendDTO> studentTrend(HttpServletRequest request) {
        try {
            return Result.success(surveyService.getStudentTrend(getUserId(request)));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/student/{surveyId}")
    public Result<SurveyDetailDTO> studentDetail(@PathVariable Long surveyId,
                                                 HttpServletRequest request) {
        try {
            return Result.success(surveyService.getDetail(surveyId, getUserId(request)));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/student/{surveyId}/submit")
    public Result<String> submit(@PathVariable Long surveyId,
                                 @RequestBody SurveySubmitRequest submitRequest,
                                 HttpServletRequest request) {
        try {
            surveyService.submit(surveyId, getUserId(request), submitRequest);
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

    private void requireSurveyManager(HttpServletRequest request) {
        Object roleObj = request.getAttribute("role");
        Integer role = roleObj == null ? null : Integer.valueOf(roleObj.toString());
        if (!Integer.valueOf(2).equals(role) && !Integer.valueOf(3).equals(role)) {
            throw new IllegalStateException("仅教师或管理员可操作");
        }
    }
}
