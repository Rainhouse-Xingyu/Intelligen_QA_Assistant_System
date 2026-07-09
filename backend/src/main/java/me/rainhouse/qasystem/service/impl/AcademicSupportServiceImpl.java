package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.rainhouse.qasystem.entity.AcademicWarningRecord;
import me.rainhouse.qasystem.entity.StudentProfile;
import me.rainhouse.qasystem.entity.StudentWarningLevel;
import me.rainhouse.qasystem.mapper.AcademicWarningRecordMapper;
import me.rainhouse.qasystem.mapper.StudentProfileMapper;
import me.rainhouse.qasystem.mapper.StudentWarningLevelMapper;
import me.rainhouse.qasystem.service.AcademicSupportService;
import me.rainhouse.qasystem.service.CozeService;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class AcademicSupportServiceImpl extends ServiceImpl<AcademicWarningRecordMapper, AcademicWarningRecord> implements AcademicSupportService {

    private static final Set<String> WARNING_LEVELS = Set.of("正常", "黄色预警", "橙色预警", "红色预警");
    private final DataFormatter dataFormatter = new DataFormatter();

    @Autowired
    private StudentProfileMapper studentProfileMapper;

    @Autowired
    private CozeService cozeService;

    @Autowired
    private StudentWarningLevelMapper studentWarningLevelMapper;

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
    public List<StudentWarningLevel> listWarningLevels() {
        return studentWarningLevelMapper.selectList(new QueryWrapper<StudentWarningLevel>()
                .orderByDesc("created_at")
                .last("LIMIT 200"));
    }

    @Override
    public StudentWarningLevel saveWarningLevel(StudentWarningLevel warningLevel) {
        if (warningLevel == null) {
            throw new IllegalArgumentException("缺少预警等级信息");
        }
        normalizeWarningLevel(warningLevel);
        StudentWarningLevel existing = null;
        if (StringUtils.hasText(warningLevel.getStudentNo())) {
            existing = studentWarningLevelMapper.selectOne(new QueryWrapper<StudentWarningLevel>()
                    .eq("student_no", warningLevel.getStudentNo())
                    .last("LIMIT 1"));
        }
        if (existing == null && warningLevel.getId() != null) {
            existing = studentWarningLevelMapper.selectById(warningLevel.getId());
        }
        if (existing == null) {
            warningLevel.setCreatedAt(LocalDateTime.now());
            studentWarningLevelMapper.insert(warningLevel);
        } else {
            warningLevel.setId(existing.getId());
            warningLevel.setCreatedAt(existing.getCreatedAt());
            studentWarningLevelMapper.updateById(warningLevel);
        }
        return warningLevel;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int importWarningLevels(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        int count = 0;
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return 0;
            }
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                StudentWarningLevel warningLevel = new StudentWarningLevel();
                warningLevel.setStudentNo(readCell(row, 0));
                warningLevel.setClassName(readCell(row, 1));
                warningLevel.setStudentName(readCell(row, 2));
                warningLevel.setWarningLevel(readCell(row, 3));
                if (!StringUtils.hasText(warningLevel.getStudentNo())
                        && !StringUtils.hasText(warningLevel.getStudentName())) {
                    continue;
                }
                saveWarningLevel(warningLevel);
                count++;
            }
            return count;
        } catch (Exception e) {
            throw new IllegalArgumentException("预警等级导入失败: " + e.getMessage(), e);
        }
    }

    private void normalizeWarningLevel(StudentWarningLevel warningLevel) {
        warningLevel.setStudentNo(trim(warningLevel.getStudentNo()));
        warningLevel.setClassName(trim(warningLevel.getClassName()));
        warningLevel.setStudentName(trim(warningLevel.getStudentName()));
        warningLevel.setWarningLevel(trim(warningLevel.getWarningLevel()));
        if (!StringUtils.hasText(warningLevel.getStudentNo())) {
            throw new IllegalArgumentException("学号不能为空");
        }
        if (!StringUtils.hasText(warningLevel.getStudentName())) {
            throw new IllegalArgumentException("姓名不能为空");
        }
        if (!WARNING_LEVELS.contains(warningLevel.getWarningLevel())) {
            throw new IllegalArgumentException("预警等级仅支持：正常、黄色预警、橙色预警、红色预警");
        }
    }

    private String readCell(Row row, int index) {
        return trim(dataFormatter.formatCellValue(row.getCell(index)));
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
