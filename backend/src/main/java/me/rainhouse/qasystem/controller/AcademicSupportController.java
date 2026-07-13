package me.rainhouse.qasystem.controller;

import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.common.dto.academic.SurveyDiagnosisDTO;
import me.rainhouse.qasystem.entity.AcademicWarningRecord;
import me.rainhouse.qasystem.entity.StudentProfile;
import me.rainhouse.qasystem.entity.StudentWarningLevel;
import me.rainhouse.qasystem.service.AcademicSupportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/academic")
public class AcademicSupportController {

    @Autowired
    private AcademicSupportService academicSupportService;

    /**
     * 【5.1模块】教务老师/辅导员触发：要求AI诊断某个学生的学业并生成帮扶策略
     */
    @PostMapping("/generate-warning")
    public Result<AcademicWarningRecord> generateWarning(
            @RequestParam("studentId") Long studentId,
            @RequestParam("term") String term) {
        try {
            AcademicWarningRecord record = academicSupportService.evaluateAndGenerateWarning(studentId, term);
            if (record == null) {
                Result<AcademicWarningRecord> res = Result.success(null);
                res.setMessage("该学生经过评判为无风险/低风险，无需生成预警计划！");
                return res;
            }
            return Result.success(record);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 【辅助接口】初始化灌入挂科和GPA数据
     */
    @PostMapping("/profile/update")
    public Result<String> updateProfile(@RequestBody StudentProfile profile) {
        academicSupportService.saveOrUpdateProfile(profile);
        return Result.success("画像数据更新成功");
    }

    @GetMapping("/warning-levels")
    public Result<List<StudentWarningLevel>> listWarningLevels() {
        return Result.success(academicSupportService.listWarningLevels());
    }

    @PostMapping("/warning-levels")
    public Result<StudentWarningLevel> saveWarningLevel(@RequestBody StudentWarningLevel warningLevel) {
        try {
            return Result.success(academicSupportService.saveWarningLevel(warningLevel));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/warning-levels/import")
    public Result<Integer> importWarningLevels(@RequestParam("file") MultipartFile file) {
        try {
            return Result.success(academicSupportService.importWarningLevels(file));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/warning-levels/template")
    public ResponseEntity<byte[]> exportWarningLevelTemplate() {
        return excelResponse(academicSupportService.exportWarningLevelTemplate(), "预警等级导入模板.xlsx");
    }

    @GetMapping("/warning-levels/export")
    public ResponseEntity<byte[]> exportWarningLevels() {
        return excelResponse(academicSupportService.exportWarningLevelsExcel(), "人工预警等级报告.xlsx");
    }

    @GetMapping("/survey-diagnoses")
    public Result<List<SurveyDiagnosisDTO>> listSurveyDiagnoses(
            @RequestParam(value = "limit", required = false) Integer limit) {
        try {
            return Result.success(academicSupportService.listSurveyDiagnoses(limit));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/survey-diagnoses/export")
    public ResponseEntity<byte[]> exportSurveyDiagnoses(
            @RequestParam(value = "limit", required = false) Integer limit) {
        return excelResponse(academicSupportService.exportSurveyDiagnosesExcel(limit), "问卷诊断与帮扶报告.xlsx");
    }

    /**
     * 【5.3模块】辅导员/管理员一键生成帮扶成效报告PDF
     */
    @PostMapping("/generate-pdf-report")
    public Result<String> generatePdfReport(@RequestParam("recordId") Long recordId) {
        try {
            String pdfUrl = academicSupportService.generateEffectivenessReportPdf(recordId);
            return Result.success(pdfUrl);
        } catch (Exception e) {
            return Result.error("报告生成失败: " + e.getMessage());
        }
    }

    private ResponseEntity<byte[]> excelResponse(byte[] bytes, String filename) {
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(bytes);
    }
}
