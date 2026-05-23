package me.rainhouse.qasystem.controller;

import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.entity.AcademicWarningRecord;
import me.rainhouse.qasystem.entity.StudentProfile;
import me.rainhouse.qasystem.service.AcademicSupportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
}