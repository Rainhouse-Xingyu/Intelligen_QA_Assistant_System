package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.rainhouse.qasystem.entity.AcademicWarningRecord;
import me.rainhouse.qasystem.entity.StudentProfile;
import me.rainhouse.qasystem.mapper.AcademicWarningRecordMapper;
import me.rainhouse.qasystem.mapper.StudentProfileMapper;
import me.rainhouse.qasystem.service.AcademicSupportService;
import me.rainhouse.qasystem.service.CozeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class AcademicSupportServiceImpl extends ServiceImpl<AcademicWarningRecordMapper, AcademicWarningRecord> implements AcademicSupportService {

    @Autowired
    private StudentProfileMapper studentProfileMapper;

    @Autowired
    private CozeService cozeService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AcademicWarningRecord evaluateAndGenerateWarning(Long studentId, String term) {
        // 1. 提取该学生的学业画像数据
        QueryWrapper<StudentProfile> qw = new QueryWrapper<>();
        qw.eq("user_id", studentId);
        StudentProfile profile = studentProfileMapper.selectOne(qw);
        
        if (profile == null) {
            throw new RuntimeException("未能找到该学生的学业画像记录");
        }

        // 2. 预测/计算风险等级 (基于规则的硬卡逻辑预判断)
        int calculatedRisk = 0; // 0-无风险
        BigDecimal gpa = profile.getGpa() != null ? profile.getGpa() : BigDecimal.ZERO;
        int failedCnt = profile.getFailedCoursesCnt() != null ? profile.getFailedCoursesCnt() : 0;
        
        if (failedCnt >= 3 || gpa.compareTo(new BigDecimal("1.5")) <= 0) {
            calculatedRisk = 3; // 高风险
        } else if (failedCnt >= 1 || gpa.compareTo(new BigDecimal("2.5")) <= 0) {
            calculatedRisk = 2; // 中风险
        } else if (gpa.compareTo(new BigDecimal("3.0")) <= 0) {
            calculatedRisk = 1; // 低风险
        }

        // 更新画像库中的红绿灯风险
        profile.setRiskLevel(calculatedRisk);
        profile.setUpdatedAt(LocalDateTime.now());
        studentProfileMapper.updateById(profile);

        // 如果没有风险或只有低风险，视情况可以不告警。这里设计为：中高风险必须产生一条归档记录。
        if (calculatedRisk < 2) {
            return null; // 不需要重点帮扶，就不生成预警表单报告了
        }

        // 3. 调用 Custom_Learning_Resources 工作流，只传脱敏后的学生标识。
        String fakeStudentId = profile.getMaskingId();
        String weakKnowledge = String.format("GPA %.2f，不及格累计门数：%d 门，需重点补齐挂科课程与前置基础知识", gpa, failedCnt);
        String warningLevel = calculatedRisk == 3 ? "红色预警" : "黄色预警";
        String surveyIndicator = profile.getPsychologicalTag() != null ? profile.getPsychologicalTag() : "暂无问卷异常指标";
        String aiResult = cozeService.generateLearningResources(fakeStudentId, weakKnowledge, warningLevel, surveyIndicator);
        
        // 简单截取或按格式拆分返回结果入库 (这里简化，整段存入计划中)
        AcademicWarningRecord record = new AcademicWarningRecord();
        record.setStudentId(profile.getUserId());
        record.setTerm(term);
        record.setWarningReason("基于成绩风控模型自动触发预警级: " + (calculatedRisk == 3 ? "高风险" : "中风险"));
        record.setAiSuggestedPlan(aiResult);
        record.setCreatedAt(LocalDateTime.now());
        
        this.save(record);
        
        return record;
    }

    @Override
    public void saveOrUpdateProfile(StudentProfile profile) {
        QueryWrapper<StudentProfile> qw = new QueryWrapper<>();
        qw.eq("user_id", profile.getUserId());
        StudentProfile exist = studentProfileMapper.selectOne(qw);
        if (exist == null) {
            studentProfileMapper.insert(profile);
        } else {
            profile.setId(exist.getId());
            profile.setUpdatedAt(LocalDateTime.now());
            studentProfileMapper.updateById(profile);
        }
    }

    @Override
    public String generateEffectivenessReportPdf(Long recordId) {
        AcademicWarningRecord record = this.getById(recordId);
        if (record == null) {
            throw new RuntimeException("预警记录不存在");
        }

        // 【5.3核心业务逻辑】：
        // 实际上这部分会使用 iTextPDF 或 PDFBox，将 record 中的 warningReason, aiSuggestedPlan 
        // 加上页眉页脚（学校logo）渲染成一个实体的 PDF文件输出到 OSS（如阿里云OSS）。
        // 为保持开发进度和演示，这里模拟了底层 PDF 渲染并返回伪造的持久化直链。
        
        String simulatedPdfUrl = "https://oss.campus.edu.cn/reports/term-" 
            + record.getTerm() + "/student-" + record.getStudentId() + "-effectiveness.pdf";

        // 更新表里面的 PDF 归档字段
        record.setReportPdfUrl(simulatedPdfUrl);
        this.updateById(record);

        return simulatedPdfUrl;
    }
}
