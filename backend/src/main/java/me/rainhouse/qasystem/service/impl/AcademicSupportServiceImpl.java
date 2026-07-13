package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.rainhouse.qasystem.common.dto.academic.SurveyDiagnosisDTO;
import me.rainhouse.qasystem.entity.AcademicWarningRecord;
import me.rainhouse.qasystem.entity.StudentProfile;
import me.rainhouse.qasystem.entity.StudentWarningLevel;
import me.rainhouse.qasystem.entity.Survey;
import me.rainhouse.qasystem.entity.SurveyAnswer;
import me.rainhouse.qasystem.entity.SurveyQuestion;
import me.rainhouse.qasystem.entity.SurveySubmission;
import me.rainhouse.qasystem.entity.SysUser;
import me.rainhouse.qasystem.mapper.AcademicWarningRecordMapper;
import me.rainhouse.qasystem.mapper.StudentProfileMapper;
import me.rainhouse.qasystem.mapper.StudentWarningLevelMapper;
import me.rainhouse.qasystem.mapper.SurveyAnswerMapper;
import me.rainhouse.qasystem.mapper.SurveyMapper;
import me.rainhouse.qasystem.mapper.SurveyQuestionMapper;
import me.rainhouse.qasystem.mapper.SurveySubmissionMapper;
import me.rainhouse.qasystem.mapper.SysUserMapper;
import me.rainhouse.qasystem.service.AcademicSupportService;
import me.rainhouse.qasystem.service.CozeService;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AcademicSupportServiceImpl extends ServiceImpl<AcademicWarningRecordMapper, AcademicWarningRecord> implements AcademicSupportService {

    private static final Set<String> WARNING_LEVELS = Set.of("正常", "黄色预警", "橙色预警", "红色预警");
    private final DataFormatter dataFormatter = new DataFormatter();
    private volatile boolean studentWarningLevelTableEnsured = false;

    @Autowired
    private StudentProfileMapper studentProfileMapper;

    @Autowired
    private CozeService cozeService;

    @Autowired
    private StudentWarningLevelMapper studentWarningLevelMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SurveySubmissionMapper surveySubmissionMapper;

    @Autowired
    private SurveyAnswerMapper surveyAnswerMapper;

    @Autowired
    private SurveyQuestionMapper surveyQuestionMapper;

    @Autowired
    private SurveyMapper surveyMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

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
        ensureStudentWarningLevelTable();
        return studentWarningLevelMapper.selectList(new QueryWrapper<StudentWarningLevel>()
                .orderByDesc("created_at")
                .last("LIMIT 200"));
    }

    @Override
    public StudentWarningLevel saveWarningLevel(StudentWarningLevel warningLevel) {
        if (warningLevel == null) {
            throw new IllegalArgumentException("缺少预警等级信息");
        }
        ensureStudentWarningLevelTable();
        normalizeWarningLevel(warningLevel);
        StudentWarningLevel existing = null;
        if (warningLevel.getId() != null) {
            existing = studentWarningLevelMapper.selectById(warningLevel.getId());
        }
        if (existing == null && StringUtils.hasText(warningLevel.getStudentNo())) {
            existing = studentWarningLevelMapper.selectOne(new QueryWrapper<StudentWarningLevel>()
                    .eq("student_no", warningLevel.getStudentNo())
                    .last("LIMIT 1"));
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
        ensureStudentWarningLevelTable();
        int count = 0;
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return 0;
            }
            int firstRow = sheet.getFirstRowNum();
            int startRow = isWarningLevelHeader(sheet.getRow(firstRow)) ? firstRow + 1 : firstRow;
            for (int rowIndex = startRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                try {
                    StudentWarningLevel warningLevel = new StudentWarningLevel();
                    warningLevel.setStudentNo(readCell(row, 0));
                    warningLevel.setClassName(readCell(row, 1));
                    warningLevel.setStudentName(readCell(row, 2));
                    warningLevel.setWarningLevel(readCell(row, 3));
                    warningLevel.setWarningReason(readCell(row, 4));
                    warningLevel.setWeaknessItems(readCell(row, 5));
                    warningLevel.setHelpMeasures(readCell(row, 6));
                    warningLevel.setCounselor(readCell(row, 7));
                    warningLevel.setContactPhone(readCell(row, 8));
                    warningLevel.setRemark(readCell(row, 9));
                    if (!StringUtils.hasText(warningLevel.getStudentNo())
                            && !StringUtils.hasText(warningLevel.getStudentName())
                            && !StringUtils.hasText(warningLevel.getWarningLevel())) {
                        continue;
                    }
                    saveWarningLevel(warningLevel);
                    count++;
                } catch (Exception rowError) {
                    throw new IllegalArgumentException("第 " + (rowIndex + 1) + " 行导入失败：" + rowError.getMessage(), rowError);
                }
            }
            return count;
        } catch (Exception e) {
            throw new IllegalArgumentException("预警等级导入失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] exportWarningLevelTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("预警等级导入模板");
            Row header = sheet.createRow(0);
            String[] columns = {
                    "学号", "班级", "姓名", "预警等级", "预警原因", "主要弱项",
                    "建议帮扶措施", "辅导员", "联系电话", "备注"
            };
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("T20210001");
            sample.createCell(1).setCellValue("软件工程2101");
            sample.createCell(2).setCellValue("张三");
            sample.createCell(3).setCellValue("黄色预警");
            sample.createCell(4).setCellValue("作业提交不稳定，近期测验成绩波动");
            sample.createCell(5).setCellValue("作业问题、考试问题");
            sample.createCell(6).setCellValue("每周一次作业跟踪，考前安排答疑");
            sample.createCell(7).setCellValue("李老师");
            sample.createCell(8).setCellValue("13800000000");
            sample.createCell(9).setCellValue("预警等级只填写：正常、黄色预警、橙色预警、红色预警");

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("导出预警等级模板失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] exportWarningLevelsExcel() {
        ensureStudentWarningLevelTable();
        List<StudentWarningLevel> rows = listWarningLevels();
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("人工预警等级报告");
            Row header = sheet.createRow(0);
            String[] columns = {"学号", "班级", "姓名", "预警等级", "预警原因", "主要弱项", "建议帮扶措施", "辅导员", "联系电话", "备注", "上传/更新时间"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }
            for (int i = 0; i < rows.size(); i++) {
                StudentWarningLevel item = rows.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(firstText(item.getStudentNo(), "", ""));
                row.createCell(1).setCellValue(firstText(item.getClassName(), "", ""));
                row.createCell(2).setCellValue(firstText(item.getStudentName(), "", ""));
                row.createCell(3).setCellValue(firstText(item.getWarningLevel(), "", ""));
                row.createCell(4).setCellValue(firstText(item.getWarningReason(), "", ""));
                row.createCell(5).setCellValue(firstText(item.getWeaknessItems(), "", ""));
                row.createCell(6).setCellValue(firstText(item.getHelpMeasures(), "", ""));
                row.createCell(7).setCellValue(firstText(item.getCounselor(), "", ""));
                row.createCell(8).setCellValue(firstText(item.getContactPhone(), "", ""));
                row.createCell(9).setCellValue(firstText(item.getRemark(), "", ""));
                row.createCell(10).setCellValue(item.getCreatedAt() == null ? "" : item.getCreatedAt().toString());
            }
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("导出预警等级报告失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<SurveyDiagnosisDTO> listSurveyDiagnoses(Integer limit) {
        int safeLimit = limit == null ? 120 : Math.max(1, Math.min(limit, 300));
        List<SurveySubmission> submissions = surveySubmissionMapper.selectList(new LambdaQueryWrapper<SurveySubmission>()
                .orderByDesc(SurveySubmission::getSubmitTime)
                .last("LIMIT " + safeLimit));
        if (submissions.isEmpty()) {
            return List.of();
        }

        List<Long> submissionIds = submissions.stream()
                .map(SurveySubmission::getId)
                .filter(Objects::nonNull)
                .toList();
        List<SurveyAnswer> answers = surveyAnswerMapper.selectList(new LambdaQueryWrapper<SurveyAnswer>()
                .in(SurveyAnswer::getSubmissionId, submissionIds));
        Map<Long, List<SurveyAnswer>> answersBySubmission = answers.stream()
                .collect(Collectors.groupingBy(SurveyAnswer::getSubmissionId));

        Map<Long, SurveyQuestion> questionMap = loadQuestionMap(answers);
        Map<Long, Survey> surveyMap = loadSurveyMap(submissions);
        Map<Long, SysUser> userMap = loadUserMap(submissions);
        Map<String, StudentWarningLevel> warningLevelMap = loadWarningLevelMap();

        return submissions.stream()
                .map(submission -> buildDiagnosis(submission,
                        surveyMap.get(submission.getSurveyId()),
                        userMap.get(submission.getUserId()),
                        answersBySubmission.getOrDefault(submission.getId(), List.of()),
                        questionMap,
                        warningLevelMap))
                .toList();
    }

    @Override
    public byte[] exportSurveyDiagnosesExcel(Integer limit) {
        List<SurveyDiagnosisDTO> rows = listSurveyDiagnoses(limit);
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("问卷诊断与帮扶报告");
            Row header = sheet.createRow(0);
            String[] columns = {
                    "学生姓名", "账号/学号", "班级", "问卷名称", "提交时间", "人工预警等级", "报告采用等级",
                    "人工预警原因", "人工主要弱项", "问卷识别弱项", "提升目标", "提升方式与跟进安排",
                    "帮扶周期", "辅导员", "联系电话", "评估方式", "报告结论", "备注"
            };
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }
            for (int i = 0; i < rows.size(); i++) {
                SurveyDiagnosisDTO item = rows.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(firstText(item.getRealName(), "", ""));
                row.createCell(1).setCellValue(firstText(item.getUsername(), String.valueOf(item.getStudentId()), ""));
                row.createCell(2).setCellValue(firstText(item.getClassName(), "", ""));
                row.createCell(3).setCellValue(firstText(item.getSurveyTitle(), "", ""));
                row.createCell(4).setCellValue(item.getSubmitTime() == null ? "" : item.getSubmitTime().toString());
                row.createCell(5).setCellValue(firstText(item.getReportWarningLevel(), "", ""));
                row.createCell(6).setCellValue(firstText(item.getFinalWarningLevel(), "", ""));
                row.createCell(7).setCellValue(firstText(item.getReportWarningReason(), "", ""));
                row.createCell(8).setCellValue(firstText(item.getReportWeaknessItems(), "", ""));
                row.createCell(9).setCellValue(firstText(item.getQuestionnaireWeaknessItems(), "", ""));
                row.createCell(10).setCellValue(firstText(item.getImprovementGoal(), "", ""));
                row.createCell(11).setCellValue(firstText(item.getHelpPlan(), item.getReportHelpMeasures(), ""));
                row.createCell(12).setCellValue(firstText(item.getSupportCycle(), "", ""));
                row.createCell(13).setCellValue(firstText(item.getReportCounselor(), "", ""));
                row.createCell(14).setCellValue(firstText(item.getReportContactPhone(), "", ""));
                row.createCell(15).setCellValue(firstText(item.getEvaluationMethod(), "", ""));
                row.createCell(16).setCellValue(firstText(item.getReportConclusion(), item.getSummary(), ""));
                row.createCell(17).setCellValue(firstText(item.getReportRemark(), "", ""));
            }
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("导出问卷诊断报告失败: " + e.getMessage(), e);
        }
    }

    private Map<Long, SurveyQuestion> loadQuestionMap(List<SurveyAnswer> answers) {
        List<Long> ids = answers.stream()
                .map(SurveyAnswer::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return surveyQuestionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SurveyQuestion::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, Survey> loadSurveyMap(List<SurveySubmission> submissions) {
        List<Long> ids = submissions.stream()
                .map(SurveySubmission::getSurveyId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return surveyMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Survey::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, SysUser> loadUserMap(List<SurveySubmission> submissions) {
        List<Long> ids = submissions.stream()
                .map(SurveySubmission::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
    }

    private Map<String, StudentWarningLevel> loadWarningLevelMap() {
        try {
            ensureStudentWarningLevelTable();
            List<StudentWarningLevel> rows = studentWarningLevelMapper.selectList(new QueryWrapper<StudentWarningLevel>()
                    .orderByDesc("created_at"));
            Map<String, StudentWarningLevel> map = new HashMap<>();
            for (StudentWarningLevel row : rows) {
                putWarningLevel(map, row.getStudentNo(), row);
                putWarningLevel(map, row.getStudentName(), row);
            }
            return map;
        } catch (Exception e) {
            log.warn("student_warning_level unavailable, survey diagnoses will be generated without uploaded report comparison: " + e.getMessage());
            return Map.of();
        }
    }

    private void ensureStudentWarningLevelTable() {
        if (studentWarningLevelTableEnsured) {
            return;
        }
        synchronized (this) {
            if (studentWarningLevelTableEnsured) {
                return;
            }
            try {
                jdbcTemplate.execute("""
                        CREATE TABLE IF NOT EXISTS `student_warning_level` (
                          `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                          `student_no` varchar(50) NOT NULL COMMENT '学号',
                          `class_name` varchar(100) DEFAULT NULL COMMENT '班级',
                          `student_name` varchar(50) NOT NULL COMMENT '学生姓名',
                          `warning_level` varchar(20) NOT NULL COMMENT '预警等级：正常/黄色预警/橙色预警/红色预警',
                          `warning_reason` varchar(500) DEFAULT NULL COMMENT '预警原因',
                          `weakness_items` varchar(500) DEFAULT NULL COMMENT '主要弱项',
                          `help_measures` varchar(1000) DEFAULT NULL COMMENT '建议帮扶措施',
                          `counselor` varchar(50) DEFAULT NULL COMMENT '辅导员',
                          `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
                          `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                          `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_student_no` (`student_no`),
                          KEY `idx_class_name` (`class_name`),
                          KEY `idx_warning_level` (`warning_level`)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生预警等级表'
                        """);
                ensureStudentWarningLevelColumn("warning_reason", "`warning_reason` varchar(500) DEFAULT NULL COMMENT '预警原因'");
                ensureStudentWarningLevelColumn("weakness_items", "`weakness_items` varchar(500) DEFAULT NULL COMMENT '主要弱项'");
                ensureStudentWarningLevelColumn("help_measures", "`help_measures` varchar(1000) DEFAULT NULL COMMENT '建议帮扶措施'");
                ensureStudentWarningLevelColumn("counselor", "`counselor` varchar(50) DEFAULT NULL COMMENT '辅导员'");
                ensureStudentWarningLevelColumn("contact_phone", "`contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话'");
                ensureStudentWarningLevelColumn("remark", "`remark` varchar(500) DEFAULT NULL COMMENT '备注'");
                studentWarningLevelTableEnsured = true;
            } catch (Exception e) {
                throw new IllegalStateException("学生预警等级表不存在，且自动建表失败，请检查数据库账号是否有 CREATE TABLE 权限：" + e.getMessage(), e);
            }
        }
    }

    private void ensureStudentWarningLevelColumn(String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'student_warning_level'
                          AND COLUMN_NAME = ?
                        """,
                Integer.class,
                columnName);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE `student_warning_level` ADD COLUMN " + columnDefinition);
        }
    }

    private void putWarningLevel(Map<String, StudentWarningLevel> map, String key, StudentWarningLevel row) {
        String normalized = normalizeCompareKey(key);
        if (StringUtils.hasText(normalized)) {
            map.putIfAbsent(normalized, row);
        }
    }

    private SurveyDiagnosisDTO buildDiagnosis(SurveySubmission submission,
                                             Survey survey,
                                             SysUser user,
                                             List<SurveyAnswer> answers,
                                             Map<Long, SurveyQuestion> questionMap,
                                             Map<String, StudentWarningLevel> warningLevelMap) {
        Map<String, DimensionAccumulator> dimensionMap = new LinkedHashMap<>();
        List<SurveyDiagnosisDTO.EvidenceAnswer> evidences = new ArrayList<>();

        for (SurveyAnswer answer : answers) {
            SurveyQuestion question = questionMap.get(answer.getQuestionId());
            String questionText = question == null ? "" : question.getQuestionText();
            String indicator = firstText(answer.getIndicatorName(), question == null ? null : question.getIndicatorName(), "");
            Dimension dimension = resolveDimension(indicator + " " + questionText);
            int riskScore = calculateRiskScore(answer, questionText, indicator);
            DimensionAccumulator accumulator = dimensionMap.computeIfAbsent(dimension.key, key -> new DimensionAccumulator(dimension));
            accumulator.add(riskScore);

            if (riskScore >= 4 || containsCriticalText(answer.getTextAnswer())) {
                evidences.add(buildEvidence(answer, dimension, questionText, riskScore));
            }
        }

        if (dimensionMap.isEmpty()) {
            dimensionMap.put(Dimension.COMPREHENSIVE.key, new DimensionAccumulator(Dimension.COMPREHENSIVE));
        }

        List<SurveyDiagnosisDTO.DimensionReport> dimensions = dimensionMap.values().stream()
                .map(DimensionAccumulator::toReport)
                .sorted(Comparator.comparing(SurveyDiagnosisDTO.DimensionReport::getScore).reversed())
                .toList();

        String surveyLevel = decideQuestionnaireRiskLevel(dimensions, evidences);
        StudentWarningLevel uploadedReport = findUploadedWarning(user, submission, warningLevelMap);
        String reportLevel = uploadedReport == null ? null : normalizeLevel(uploadedReport.getWarningLevel());
        String finalLevel = decideFinalLevel(reportLevel, surveyLevel);
        Dimension primary = findDimension(dimensions.isEmpty() ? null : dimensions.get(0).getDimension());
        Dimension secondary = dimensions.size() > 1 ? findDimension(dimensions.get(1).getDimension()) : null;

        SurveyDiagnosisDTO dto = new SurveyDiagnosisDTO();
        dto.setSubmissionId(submission.getId());
        dto.setSurveyId(submission.getSurveyId());
        dto.setSurveyTitle(survey == null ? "未命名问卷" : survey.getTitle());
        dto.setStudentId(submission.getUserId());
        dto.setUsername(user == null ? String.valueOf(submission.getUserId()) : user.getUsername());
        dto.setRealName(user == null ? "学生" + submission.getUserId() : firstText(user.getRealName(), user.getUsername(), "学生" + submission.getUserId()));
        dto.setClassName(uploadedReport == null ? null : uploadedReport.getClassName());
        dto.setSubmitTime(submission.getSubmitTime());
        dto.setWarningLevel(finalLevel);
        dto.setReportWarningLevel(reportLevel == null ? "未上传预警报告" : reportLevel);
        dto.setReportWarningReason(uploadedReport == null ? null : uploadedReport.getWarningReason());
        dto.setReportWeaknessItems(uploadedReport == null ? null : uploadedReport.getWeaknessItems());
        dto.setReportHelpMeasures(uploadedReport == null ? null : uploadedReport.getHelpMeasures());
        dto.setReportCounselor(uploadedReport == null ? null : uploadedReport.getCounselor());
        dto.setReportContactPhone(uploadedReport == null ? null : uploadedReport.getContactPhone());
        dto.setReportRemark(uploadedReport == null ? null : uploadedReport.getRemark());
        dto.setSurveyWarningLevel(surveyLevel);
        dto.setFinalWarningLevel(finalLevel);
        dto.setComparisonResult(buildComparisonResult(reportLevel, surveyLevel));
        dto.setComparisonDetail(buildComparisonDetail(reportLevel, surveyLevel, uploadedReport));
        dto.setPrimaryProblem(primary.label);
        dto.setSecondaryProblem(secondary == null ? null : secondary.label);
        dto.setDimensions(dimensions);
        dto.setEvidences(evidences.stream().limit(8).toList());
        dto.setQuestionnaireWeaknessItems(firstText(formatWeakAreas(dimensions), primary.label, "综合学习状态"));
        dto.setSupportCycle(buildSupportCycle(finalLevel));
        dto.setImprovementGoal(buildImprovementGoal(dto));
        dto.setEvaluationMethod(buildEvaluationMethod(finalLevel));
        dto.setHelpPlan(buildHelpPlan(finalLevel, reportLevel, surveyLevel, uploadedReport, primary, secondary));
        dto.setSummary(buildSummary(dto, dimensions));
        dto.setReportConclusion(buildReportConclusion(dto, reportLevel, surveyLevel));
        return dto;
    }

    private StudentWarningLevel findUploadedWarning(SysUser user,
                                                    SurveySubmission submission,
                                                    Map<String, StudentWarningLevel> warningLevelMap) {
        if (warningLevelMap == null || warningLevelMap.isEmpty()) {
            return null;
        }
        List<String> keys = new ArrayList<>();
        if (user != null) {
            keys.add(user.getUsername());
            keys.add(user.getRealName());
        }
        keys.add(String.valueOf(submission.getUserId()));
        for (String key : keys) {
            StudentWarningLevel row = warningLevelMap.get(normalizeCompareKey(key));
            if (row != null) {
                return row;
            }
        }
        return null;
    }

    private SurveyDiagnosisDTO.EvidenceAnswer buildEvidence(SurveyAnswer answer, Dimension dimension, String questionText, int riskScore) {
        SurveyDiagnosisDTO.EvidenceAnswer evidence = new SurveyDiagnosisDTO.EvidenceAnswer();
        evidence.setQuestionId(answer.getQuestionId());
        evidence.setDimension(dimension.label);
        evidence.setQuestionText(questionText);
        evidence.setAnswerText(formatAnswer(answer, questionText));
        evidence.setRiskScore(riskScore);
        evidence.setRiskPoint(riskScore >= 5 ? "高风险回答" : "需关注回答");
        return evidence;
    }

    private String decideQuestionnaireRiskLevel(List<SurveyDiagnosisDTO.DimensionReport> dimensions,
                                                List<SurveyDiagnosisDTO.EvidenceAnswer> evidences) {
        long serious = dimensions.stream().filter(item -> item.getScore() >= 4.0).count();
        long abnormal = dimensions.stream().filter(item -> item.getScore() >= 3.2).count();
        boolean critical = evidences.stream().anyMatch(item -> item.getRiskScore() >= 5
                && ("心理状态".equals(item.getDimension()) || item.getQuestionText().contains("退学")));
        if (critical || serious >= 2 || abnormal >= 3) {
            return "红色预警";
        }
        if (serious >= 1 || abnormal >= 2) {
            return "橙色预警";
        }
        return "黄色预警";
    }

    private String buildSummary(SurveyDiagnosisDTO dto, List<SurveyDiagnosisDTO.DimensionReport> dimensions) {
        boolean hasManualReport = dto.getReportWarningLevel() != null && !dto.getReportWarningLevel().contains("未上传");
        String levelText = dto.getFinalWarningLevel().replace("预警", "");
        String weakAreas = formatWeakAreas(dimensions);
        if (hasManualReport) {
            return String.format("%s的人工预警等级为%s，本报告以人工预警文档为准；学生问卷分数已保存并用于识别弱项。系统识别的重点帮扶方向为：%s。建议按照“%s”执行，并在周期结束后复评。",
                    dto.getRealName(), levelText, firstText(weakAreas, "综合学习状态", "综合学习状态"), buildSupportCycle(dto.getFinalWarningLevel()));
        }
        return String.format("%s尚未匹配到人工上传或手动设置的预警等级。本系统已保存该学生问卷分数，并识别弱项为：%s。请先补录人工预警等级，再导出正式帮扶报告。",
                dto.getRealName(), firstText(weakAreas, "综合学习状态", "综合学习状态"));
    }

    private String buildHelpPlan(String finalLevel,
                                 String reportLevel,
                                 String surveyLevel,
                                 StudentWarningLevel uploadedReport,
                                 Dimension primary,
                                 Dimension secondary) {
        List<String> lines = new ArrayList<>();
        lines.add("一、帮扶周期：" + buildSupportCycle(finalLevel));
        lines.add("二、主要弱项：" + primary.label + (secondary != null && secondary != primary ? "、" + secondary.label : ""));
        if (uploadedReport != null && StringUtils.hasText(uploadedReport.getHelpMeasures())) {
            lines.add("三、人工报告建议：" + uploadedReport.getHelpMeasures());
        }
        lines.add((uploadedReport != null && StringUtils.hasText(uploadedReport.getHelpMeasures()) ? "四" : "三") + "、问卷弱项提升：" + primary.plan);
        if (secondary != null && secondary != primary) {
            lines.add((uploadedReport != null && StringUtils.hasText(uploadedReport.getHelpMeasures()) ? "五" : "四") + "、辅助提升：" + secondary.plan);
        }
        lines.add("过程跟踪：每周记录作业完成率、课堂参与、阶段测验表现和学生反馈。");
        lines.add("复评方式：周期结束后重新查看问卷弱项分数变化，并由辅导员结合访谈给出是否继续帮扶的结论。");
        if (!StringUtils.hasText(reportLevel)) {
            lines.add("报告状态：当前缺少人工预警等级，需补录后再作为正式报告使用。");
        } else if ("红色预警".equals(finalLevel)) {
            lines.add("重点要求：红色预警学生建议辅导员重点跟进，必要时联动任课教师、心理中心或学院学业预警流程。");
        }
        return String.join("\n", lines);
    }

    private String decideFinalLevel(String reportLevel, String surveyLevel) {
        if (!StringUtils.hasText(reportLevel)) {
            return "未上传预警报告";
        }
        return reportLevel;
    }

    private String buildComparisonResult(String reportLevel, String surveyLevel) {
        if (!StringUtils.hasText(reportLevel)) {
            return "缺少人工预警报告，无法生成正式等级比对";
        }
        int reportRank = levelRank(reportLevel);
        int surveyRank = levelRank(surveyLevel);
        if (reportRank == surveyRank) {
            return "人工等级与问卷风险参考一致";
        }
        if (surveyRank > reportRank) {
            return "问卷风险参考高于人工等级，建议复核";
        }
        return "人工等级高于问卷风险参考，建议持续观察";
    }

    private String buildComparisonDetail(String reportLevel, String surveyLevel, StudentWarningLevel uploadedReport) {
        if (uploadedReport == null) {
            return "当前学生未匹配到上传或手动设置的预警等级报告记录。系统不会用问卷分数直接给出正式预警等级，请先补录人工预警等级。";
        }
        String detail = String.format("匹配到人工预警报告：学号%s，姓名%s，人工等级%s；问卷风险参考%s。正式报告采用人工等级，问卷分数只用于解释差异和定位弱项。",
                firstText(uploadedReport.getStudentNo(), "-", "-"),
                firstText(uploadedReport.getStudentName(), "-", "-"),
                reportLevel,
                surveyLevel);
        List<String> extras = new ArrayList<>();
        if (StringUtils.hasText(uploadedReport.getWarningReason())) {
            extras.add("预警原因：" + uploadedReport.getWarningReason());
        }
        if (StringUtils.hasText(uploadedReport.getWeaknessItems())) {
            extras.add("主要弱项：" + uploadedReport.getWeaknessItems());
        }
        if (StringUtils.hasText(uploadedReport.getHelpMeasures())) {
            extras.add("人工建议措施：" + uploadedReport.getHelpMeasures());
        }
        if (StringUtils.hasText(uploadedReport.getCounselor())) {
            extras.add("辅导员：" + uploadedReport.getCounselor());
        }
        if (StringUtils.hasText(uploadedReport.getContactPhone())) {
            extras.add("联系电话：" + uploadedReport.getContactPhone());
        }
        return extras.isEmpty() ? detail : detail + " " + String.join("；", extras) + "。";
    }

    private String normalizeLevel(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String level = value.trim();
        if ("红".equals(level) || level.contains("红")) return "红色预警";
        if ("橙".equals(level) || level.contains("橙")) return "橙色预警";
        if ("黄".equals(level) || level.contains("黄")) return "黄色预警";
        if ("正常".equals(level) || level.contains("无风险")) return "正常";
        return level;
    }

    private int levelRank(String level) {
        if (!StringUtils.hasText(level)) return -1;
        if (level.contains("红")) return 3;
        if (level.contains("橙")) return 2;
        if (level.contains("黄")) return 1;
        return 0;
    }

    private String normalizeCompareKey(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : null;
    }

    private int calculateRiskScore(SurveyAnswer answer, String questionText, String indicator) {
        if (answer.getNumericAnswer() != null) {
            int value = Math.max(1, Math.min(5, answer.getNumericAnswer()));
            String text = (indicator + " " + questionText).toLowerCase();
            if (text.contains("帮扶频率")) {
                return value == 5 ? 1 : Math.min(5, value + 1);
            }
            return isNegativeQuestion(text) ? 6 - value : value;
        }
        String textAnswer = answer.getTextAnswer();
        if (!StringUtils.hasText(textAnswer)) {
            return 1;
        }
        if (containsCriticalText(textAnswer)) {
            return 5;
        }
        if (containsAny(textAnswer, "不会", "困难", "压力", "焦虑", "拖延", "挂科", "不懂", "跟不上", "作业", "考试")) {
            return 4;
        }
        return 2;
    }

    private boolean isNegativeQuestion(String text) {
        return containsAny(text,
                "焦虑", "压力", "担心", "害怕", "困难", "不懂", "不会", "拖延", "缺交", "未完成",
                "挂科", "旷课", "缺勤", "失眠", "低落", "迷茫", "退学", "放弃", "跟不上", "冲突",
                "很少", "缺乏", "无法", "难以");
    }

    private boolean containsCriticalText(String value) {
        return containsAny(value, "自杀", "伤害自己", "活不下去", "崩溃", "退学", "长期失眠", "严重焦虑");
    }

    private boolean containsAny(String value, String... keywords) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String lower = value.toLowerCase();
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String formatAnswer(SurveyAnswer answer, String questionText) {
        if (answer.getNumericAnswer() == null) {
            return firstText(answer.getTextAnswer(), "未填写", "未填写");
        }
        int value = answer.getNumericAnswer();
        if (questionText != null && questionText.contains("帮扶频率")) {
            return switch (value) {
                case 1 -> "1 - 每月1次";
                case 2 -> "2 - 每两周1次";
                case 3 -> "3 - 每周1次";
                case 4 -> "4 - 每周2次";
                case 5 -> "5 - 不需要帮扶";
                default -> String.valueOf(value);
            };
        }
        return switch (value) {
            case 1 -> "1 - 完全符合";
            case 2 -> "2 - 比较符合";
            case 3 -> "3 - 一般符合";
            case 4 -> "4 - 比较不符合";
            case 5 -> "5 - 不符合";
            default -> String.valueOf(value);
        };
    }

    private Dimension resolveDimension(String rawText) {
        String text = rawText == null ? "" : rawText.toLowerCase();
        if (containsAny(text, "作业", "任务", "提交", "完成", "拖延")) return Dimension.HOMEWORK;
        if (containsAny(text, "考试", "复习", "挂科", "成绩", "测验", "考前")) return Dimension.EXAM;
        if (containsAny(text, "课程", "听懂", "知识点", "基础", "课堂", "理解", "跟不上")) return Dimension.COURSE;
        if (containsAny(text, "时间", "计划", "熬夜", "作息", "安排")) return Dimension.TIME;
        if (containsAny(text, "心理", "焦虑", "压力", "情绪", "低落", "失眠", "自信")) return Dimension.PSYCHOLOGY;
        if (containsAny(text, "目标", "动力", "主动", "兴趣", "自律")) return Dimension.MOTIVATION;
        if (containsAny(text, "宿舍", "同学", "人际", "家庭", "生活")) return Dimension.LIFE;
        return Dimension.COMPREHENSIVE;
    }

    private Dimension findDimension(String label) {
        if (!StringUtils.hasText(label)) {
            return Dimension.COMPREHENSIVE;
        }
        for (Dimension dimension : Dimension.values()) {
            if (dimension.label.equals(label) || dimension.key.equals(label)) {
                return dimension;
            }
        }
        return Dimension.COMPREHENSIVE;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String formatDimensions(List<SurveyDiagnosisDTO.DimensionReport> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return "";
        }
        return dimensions.stream()
                .map(item -> item.getLabel() + "：" + String.format("%.1f", item.getScore()) + "（" + item.getStatus() + "）")
                .collect(Collectors.joining("；"));
    }

    private String formatWeakAreas(List<SurveyDiagnosisDTO.DimensionReport> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return "";
        }
        return dimensions.stream()
                .filter(item -> item.getScore() == null || item.getScore() >= 2.7)
                .limit(4)
                .map(SurveyDiagnosisDTO.DimensionReport::getLabel)
                .distinct()
                .collect(Collectors.joining("、"));
    }

    private String buildImprovementGoal(SurveyDiagnosisDTO item) {
        String weakAreas = firstText(item.getQuestionnaireWeaknessItems(), item.getReportWeaknessItems(), "综合学习状态");
        return "围绕" + weakAreas + "进行专项提升，周期结束后弱项表现较当前明显改善，形成稳定学习计划和反馈记录。";
    }

    private String buildSupportCycle(String level) {
        if (!StringUtils.hasText(level) || level.contains("未上传")) {
            return "待补录人工预警等级后确定周期";
        }
        if (level.contains("红")) {
            return "8周重点帮扶：每周跟进1次，第4周阶段复盘，第8周复评";
        }
        if (level.contains("橙")) {
            return "6周专项帮扶：每两周跟进1次，第6周复评";
        }
        if (level.contains("黄")) {
            return "4周观察帮扶：每两周跟进1次，第4周复评";
        }
        return "2-4周常规观察：根据学生状态安排复评";
    }

    private String buildEvaluationMethod(String level) {
        if (!StringUtils.hasText(level) || level.contains("未上传")) {
            return "补录人工预警等级后，再结合问卷弱项分数、作业完成率、阶段测验表现和辅导员访谈记录进行复评。";
        }
        if (level.contains("红")) {
            return "每周记录一次过程表现，第4周进行阶段复盘，第8周结合问卷弱项变化、课程成绩、作业完成率、访谈记录形成复评结论。";
        }
        if (level.contains("橙")) {
            return "每两周记录一次过程表现，第6周结合问卷弱项变化、作业完成率、阶段测验表现和辅导员访谈记录进行复评。";
        }
        if (level.contains("黄")) {
            return "第2周进行一次跟进，第4周结合问卷弱项变化、课堂参与和作业完成率进行复评。";
        }
        return "周期结束后结合问卷弱项变化、学习过程记录和辅导员访谈记录进行复评。";
    }

    private String buildReportConclusion(SurveyDiagnosisDTO dto, String reportLevel, String surveyLevel) {
        String weakAreas = firstText(dto.getQuestionnaireWeaknessItems(), dto.getReportWeaknessItems(), "综合学习状态");
        if (!StringUtils.hasText(reportLevel)) {
            return String.format("%s尚未匹配人工预警等级，本报告暂作为问卷弱项参考。请老师上传或手动设置人工预警报告后，再生成正式帮扶报告。当前问卷识别弱项为：%s。",
                    dto.getRealName(), weakAreas);
        }
        String comparison = levelRank(surveyLevel) > levelRank(reportLevel)
                ? "问卷风险参考高于人工等级，建议老师复核近期学习过程材料。"
                : levelRank(surveyLevel) < levelRank(reportLevel)
                ? "人工等级高于问卷风险参考，说明仍需结合成绩、考勤或教师反馈持续观察。"
                : "人工等级与问卷风险参考基本一致。";
        return String.format("本报告采用人工预警等级“%s”作为正式等级，问卷分数不直接决定预警，只用于定位弱项。系统识别该生重点弱项为：%s。%s建议按照“%s”开展帮扶，并在周期结束后复评。",
                reportLevel, weakAreas, comparison, dto.getSupportCycle());
    }

    private String formatEvidences(List<SurveyDiagnosisDTO.EvidenceAnswer> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return "";
        }
        return evidences.stream()
                .map(item -> item.getDimension() + "｜" + item.getQuestionText() + "｜" + item.getAnswerText()
                        + "｜风险分" + item.getRiskScore())
                .collect(Collectors.joining("\n"));
    }

    private static class DimensionAccumulator {
        private final Dimension dimension;
        private int total;
        private int count;

        DimensionAccumulator(Dimension dimension) {
            this.dimension = dimension;
        }

        void add(int riskScore) {
            total += riskScore;
            count++;
        }

        SurveyDiagnosisDTO.DimensionReport toReport() {
            double score = count == 0 ? 1.0 : Math.round((total * 10.0 / count)) / 10.0;
            SurveyDiagnosisDTO.DimensionReport report = new SurveyDiagnosisDTO.DimensionReport();
            report.setDimension(dimension.label);
            report.setLabel(dimension.label);
            report.setScore(score);
            report.setAnswerCount(count);
            report.setStatus(score >= 4.0 ? "严重异常" : score >= 3.2 ? "明显偏弱" : score >= 2.7 ? "轻度关注" : "基本稳定");
            report.setReason(dimension.reason);
            return report;
        }
    }

    private enum Dimension {
        HOMEWORK("homework", "作业问题", "作业完成、提交及时性或主动求助方面存在风险。", "围绕作业建立每周任务清单，安排学习委员/任课教师答疑，并跟踪两周作业完成率。"),
        EXAM("exam", "考试问题", "考试压力、复习计划或挂科风险需要关注。", "帮助学生拆分复习计划，明确重点题型和时间表，考前安排一次针对性答疑。"),
        COURSE("course", "课程理解", "课程知识点、课堂理解或基础能力存在薄弱点。", "定位薄弱课程和知识点，推荐资料或知识库内容，安排同伴帮扶或教师答疑。"),
        TIME("time", "时间管理", "学习计划、作息或任务安排存在不稳定。", "协助制定一周学习时间表，将大任务拆成每日可完成的小目标。"),
        PSYCHOLOGY("psychology", "心理状态", "焦虑、压力、低落或睡眠等状态需要关注。", "先进行支持性谈话，必要时建议心理中心或辅导员持续跟进。"),
        MOTIVATION("motivation", "学习动力", "学习目标、主动性或自我效能感不足。", "帮助学生设定短期可达成目标，并用阶段性反馈增强学习信心。"),
        LIFE("life", "生活适应", "生活、人际或家庭因素可能影响学习状态。", "了解生活和人际压力来源，必要时协调班委、辅导员或家校沟通资源。"),
        COMPREHENSIVE("comprehensive", "综合状态", "问卷未能归入单一维度，需要老师结合原始回答综合判断。", "结合原始回答做一次简短访谈，再确定是否需要专项帮扶。");

        private final String key;
        private final String label;
        private final String reason;
        private final String plan;

        Dimension(String key, String label, String reason, String plan) {
            this.key = key;
            this.label = label;
            this.reason = reason;
            this.plan = plan;
        }
    }

    private void normalizeWarningLevel(StudentWarningLevel warningLevel) {
        warningLevel.setStudentNo(trim(warningLevel.getStudentNo()));
        warningLevel.setClassName(trim(warningLevel.getClassName()));
        warningLevel.setStudentName(trim(warningLevel.getStudentName()));
        warningLevel.setWarningLevel(normalizeImportedWarningLevel(warningLevel.getWarningLevel()));
        warningLevel.setWarningReason(trim(warningLevel.getWarningReason()));
        warningLevel.setWeaknessItems(trim(warningLevel.getWeaknessItems()));
        warningLevel.setHelpMeasures(trim(warningLevel.getHelpMeasures()));
        warningLevel.setCounselor(trim(warningLevel.getCounselor()));
        warningLevel.setContactPhone(trim(warningLevel.getContactPhone()));
        warningLevel.setRemark(trim(warningLevel.getRemark()));
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

    private boolean isWarningLevelHeader(Row row) {
        if (row == null) {
            return false;
        }
        String first = readCell(row, 0);
        String second = readCell(row, 1);
        String third = readCell(row, 2);
        String fourth = readCell(row, 3);
        String combined = firstText(first, "", "") + firstText(second, "", "")
                + firstText(third, "", "") + firstText(fourth, "", "");
        return combined.contains("学号") || combined.contains("班级")
                || combined.contains("姓名") || combined.contains("预警");
    }

    private String normalizeImportedWarningLevel(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim()
                .replace(" ", "")
                .replace("　", "")
                .replace("等级", "")
                .replace("级别", "");
        if ("0".equals(normalized) || normalized.contains("正常") || normalized.contains("无风险") || normalized.contains("无预警")) {
            return "正常";
        }
        if ("1".equals(normalized) || "黄".equals(normalized) || normalized.contains("黄色") || normalized.contains("黄预警")) {
            return "黄色预警";
        }
        if ("2".equals(normalized) || "橙".equals(normalized) || normalized.contains("橙色") || normalized.contains("橙预警")) {
            return "橙色预警";
        }
        if ("3".equals(normalized) || "红".equals(normalized) || normalized.contains("红色") || normalized.contains("红预警")) {
            return "红色预警";
        }
        return normalized;
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
