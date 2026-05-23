package me.rainhouse.qasystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.rainhouse.qasystem.entity.AcademicWarningRecord;
import me.rainhouse.qasystem.entity.StudentProfile;

public interface AcademicSupportService extends IService<AcademicWarningRecord> {
    
    /**
     * 【5.1模块】学业帮扶预警评估与预测
     * 根据学生的画像（GPA，挂科情况），更新风险等级，并调用大模型生成预警判定及帮扶方案
     * 
     * @param studentId 学生系统ID
     * @param term 当前学期，例如 "2026-春"
     * @return 刚生成的预警记录
     */
    AcademicWarningRecord evaluateAndGenerateWarning(Long studentId, String term);

    /**
     * 更新或插入学生基础画像数据（辅助测试使用）
     */
    void saveOrUpdateProfile(StudentProfile profile);

    /**
     * 【5.3模块】帮扶成效报告自动化生成（模拟生成可下载的报告URL）
     * 
     * @param recordId AcademicWarningRecord 的主键ID
     * @return PDF文档的网络访问URL
     */
    String generateEffectivenessReportPdf(Long recordId);
}