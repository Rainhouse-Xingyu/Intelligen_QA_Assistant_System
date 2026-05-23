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

        // 3. 构建发送给 AI 模型的 Prompt（组合隐私脱敏信息）
        // 重点：不发送真名、真实System ID，只发送脱敏 maskingId
        String prompt = String.format(
            "请作为高校学业指导专家，为代号为【%s】的学生撰写一份结构化学业预警分析和帮扶干预方案。\n" +
            "学生当前情况：GPA %.2f，不及格累计门数：%d 门，当前性格标签备注为：%s。\n" +
            "请输出两部分：\n" +
            "1. 预警原因深度分析（30-50字）\n" +
            "2. 具体帮扶动作方案（分点说明，可操作性强，例如针对心理或挂科提出补救意见）\n" +
            "使用 Markdown 格式返回。",
            profile.getMaskingId(), gpa, failedCnt, profile.getPsychologicalTag() != null ? profile.getPsychologicalTag() : "无"
        );

        // 4. 调用 Coze AI 获取大模型决策（这里复用大语言模型的推断能力）
        String aiResult = cozeService.chat(String.valueOf(profile.getUserId()), prompt);
        
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
}