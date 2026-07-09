package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.common.dto.survey.SurveyAnswerRequest;
import me.rainhouse.qasystem.common.dto.survey.SurveyDetailDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveyQuestionDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveySubmissionAnswerDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveySubmissionDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveySubmitRequest;
import me.rainhouse.qasystem.common.dto.survey.SurveyTaskRequest;
import me.rainhouse.qasystem.common.dto.survey.SurveyTrendDTO;
import me.rainhouse.qasystem.entity.Survey;
import me.rainhouse.qasystem.entity.SurveyAnswer;
import me.rainhouse.qasystem.entity.SurveyQuestion;
import me.rainhouse.qasystem.entity.SurveySubmission;
import me.rainhouse.qasystem.entity.SurveyTemplate;
import me.rainhouse.qasystem.entity.SurveyTemplateQuestion;
import me.rainhouse.qasystem.entity.SysUser;
import me.rainhouse.qasystem.mapper.SurveyAnswerMapper;
import me.rainhouse.qasystem.mapper.SurveyMapper;
import me.rainhouse.qasystem.mapper.SurveyQuestionMapper;
import me.rainhouse.qasystem.mapper.SurveySubmissionMapper;
import me.rainhouse.qasystem.mapper.SurveyTemplateMapper;
import me.rainhouse.qasystem.mapper.SurveyTemplateQuestionMapper;
import me.rainhouse.qasystem.service.SysUserService;
import me.rainhouse.qasystem.service.SurveyService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SurveyServiceImpl implements SurveyService {

    private static final int SKIP_EXPORT_COLUMNS = 4;
    private static final int QUESTION_TYPE_SCALE = 1;
    private static final int QUESTION_TYPE_TEXT = 2;

    private final SurveyMapper surveyMapper;
    private final SurveyQuestionMapper surveyQuestionMapper;
    private final SurveySubmissionMapper surveySubmissionMapper;
    private final SurveyAnswerMapper surveyAnswerMapper;
    private final SurveyTemplateMapper surveyTemplateMapper;
    private final SurveyTemplateQuestionMapper surveyTemplateQuestionMapper;
    private final SysUserService sysUserService;
    private final DataFormatter dataFormatter = new DataFormatter();

    public SurveyServiceImpl(SurveyMapper surveyMapper,
                             SurveyQuestionMapper surveyQuestionMapper,
                             SurveySubmissionMapper surveySubmissionMapper,
                             SurveyAnswerMapper surveyAnswerMapper,
                             SurveyTemplateMapper surveyTemplateMapper,
                             SurveyTemplateQuestionMapper surveyTemplateQuestionMapper,
                             SysUserService sysUserService) {
        this.surveyMapper = surveyMapper;
        this.surveyQuestionMapper = surveyQuestionMapper;
        this.surveySubmissionMapper = surveySubmissionMapper;
        this.surveyAnswerMapper = surveyAnswerMapper;
        this.surveyTemplateMapper = surveyTemplateMapper;
        this.surveyTemplateQuestionMapper = surveyTemplateQuestionMapper;
        this.sysUserService = sysUserService;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Survey importTemplate(MultipartFile file, String title, String description, Long adminId) {
        SurveyTemplate template = importSurveyTemplate(file, title, description, adminId);
        SurveyTaskRequest request = new SurveyTaskRequest();
        request.setTemplateId(template.getId());
        request.setTitle(StringUtils.hasText(title) ? title.trim() : template.getName());
        request.setDescription(description);
        request.setScopeType("ALL");
        request.setPublishNow(false);
        return createTask(request, adminId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SurveyTemplate importSurveyTemplate(MultipartFile file, String name, String description, Long userId) {
        validateTemplateFile(file);
        List<QuestionDraft> drafts = readQuestionDrafts(file);
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException("模板中未读取到问卷题目");
        }

        SurveyTemplate template = new SurveyTemplate();
        template.setName(StringUtils.hasText(name) ? name.trim() : stripExtension(file.getOriginalFilename()));
        template.setDescription(StringUtils.hasText(description) ? description.trim() : null);
        template.setFileName(file.getOriginalFilename());
        template.setCreatedBy(userId);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        surveyTemplateMapper.insert(template);

        for (int i = 0; i < drafts.size(); i++) {
            QuestionDraft draft = drafts.get(i);
            SurveyTemplateQuestion question = new SurveyTemplateQuestion();
            question.setTemplateId(template.getId());
            question.setQuestionNo(i + 1);
            question.setQuestionCode(draft.questionCode());
            question.setIndicatorName(draft.indicatorName());
            question.setQuestionText(draft.questionText());
            question.setQuestionType(draft.questionType());
            question.setRequired(draft.required());
            question.setSortOrder(i + 1);
            question.setCreatedAt(LocalDateTime.now());
            surveyTemplateQuestionMapper.insert(question);
        }
        log.info("survey template imported: templateId={}, questions={}", template.getId(), drafts.size());
        return template;
    }

    @Override
    public List<SurveyTemplate> listTemplates() {
        return surveyTemplateMapper.selectList(new LambdaQueryWrapper<SurveyTemplate>()
                .orderByDesc(SurveyTemplate::getUpdatedAt)
                .orderByDesc(SurveyTemplate::getCreatedAt));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Survey createTask(SurveyTaskRequest request, Long userId) {
        if (request == null || request.getTemplateId() == null) {
            throw new IllegalArgumentException("请选择问卷模板");
        }
        SurveyTemplate template = surveyTemplateMapper.selectById(request.getTemplateId());
        if (template == null) {
            throw new IllegalArgumentException("问卷模板不存在");
        }
        List<SurveyTemplateQuestion> templateQuestions = listTemplateQuestions(template.getId());
        if (templateQuestions.isEmpty()) {
            throw new IllegalArgumentException("问卷模板没有题目");
        }
        validateTimeRange(request.getStartTime(), request.getEndTime());

        Survey survey = new Survey();
        survey.setTemplateId(template.getId());
        survey.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle().trim() : template.getName());
        survey.setSubject(StringUtils.hasText(request.getSubject()) ? request.getSubject().trim() : null);
        survey.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : template.getDescription());
        survey.setPurpose(StringUtils.hasText(request.getPurpose()) ? request.getPurpose().trim() : null);
        survey.setScopeType(StringUtils.hasText(request.getScopeType()) ? request.getScopeType().trim() : "ALL");
        survey.setScopeText(StringUtils.hasText(request.getScopeText()) ? request.getScopeText().trim() : null);
        survey.setPublisherId(userId);
        survey.setAcademicYear(request.getAcademicYear());
        survey.setTermNo(request.getTermNo());
        survey.setCreatedBy(userId);
        survey.setStartTime(request.getStartTime());
        survey.setEndTime(request.getEndTime());
        survey.setStatus(Boolean.TRUE.equals(request.getPublishNow()) ? 1 : 0);
        survey.setCreatedAt(LocalDateTime.now());
        survey.setUpdatedAt(LocalDateTime.now());
        if (Integer.valueOf(1).equals(survey.getStatus())) {
            survey.setPublishedAt(LocalDateTime.now());
        }
        surveyMapper.insert(survey);

        for (SurveyTemplateQuestion templateQuestion : templateQuestions) {
            SurveyQuestion question = new SurveyQuestion();
            question.setSurveyId(survey.getId());
            question.setTemplateQuestionId(templateQuestion.getId());
            question.setQuestionNo(templateQuestion.getQuestionNo());
            question.setQuestionCode(templateQuestion.getQuestionCode());
            question.setIndicatorName(templateQuestion.getIndicatorName());
            question.setQuestionText(templateQuestion.getQuestionText());
            question.setQuestionType(templateQuestion.getQuestionType());
            question.setRequired(templateQuestion.getRequired());
            question.setSortOrder(templateQuestion.getSortOrder());
            question.setCreatedAt(LocalDateTime.now());
            surveyQuestionMapper.insert(question);
        }
        return survey;
    }

    @Override
    public List<Survey> listAdminSurveys() {
        return surveyMapper.selectList(new LambdaQueryWrapper<Survey>()
                .orderByDesc(Survey::getUpdatedAt)
                .orderByDesc(Survey::getCreatedAt));
    }

    @Override
    public SurveyDetailDTO getDetail(Long surveyId, Long userId) {
        Survey survey = requireSurvey(surveyId);
        SurveyDetailDTO dto = new SurveyDetailDTO();
        dto.setSurvey(survey);
        dto.setQuestions(listQuestions(surveyId).stream().map(this::toQuestionDTO).toList());
        if (userId != null) {
            SurveySubmission submission = findSubmission(surveyId, userId);
            dto.setSubmitted(submission != null);
            dto.setSubmissionId(submission == null ? null : submission.getId());
        } else {
            dto.setSubmitted(false);
        }
        return dto;
    }

    @Override
    public Survey publish(Long surveyId) {
        Survey survey = requireSurvey(surveyId);
        Long questionCount = surveyQuestionMapper.selectCount(new LambdaQueryWrapper<SurveyQuestion>()
                .eq(SurveyQuestion::getSurveyId, surveyId));
        if (questionCount == null || questionCount == 0) {
            throw new IllegalArgumentException("问卷没有题目，无法发布");
        }
        validateTimeRange(survey.getStartTime(), survey.getEndTime());
        survey.setStatus(1);
        survey.setPublishedAt(LocalDateTime.now());
        survey.setUpdatedAt(LocalDateTime.now());
        surveyMapper.updateById(survey);
        return survey;
    }

    @Override
    public Survey close(Long surveyId) {
        Survey survey = requireSurvey(surveyId);
        survey.setStatus(2);
        survey.setUpdatedAt(LocalDateTime.now());
        surveyMapper.updateById(survey);
        return survey;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteSurvey(Long surveyId) {
        requireSurvey(surveyId);
        surveyAnswerMapper.delete(new LambdaQueryWrapper<SurveyAnswer>()
                .eq(SurveyAnswer::getSurveyId, surveyId));
        surveySubmissionMapper.delete(new LambdaQueryWrapper<SurveySubmission>()
                .eq(SurveySubmission::getSurveyId, surveyId));
        surveyQuestionMapper.delete(new LambdaQueryWrapper<SurveyQuestion>()
                .eq(SurveyQuestion::getSurveyId, surveyId));
        surveyMapper.deleteById(surveyId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteSubmission(Long submissionId) {
        if (submissionId == null) {
            throw new IllegalArgumentException("缺少提交记录ID");
        }
        SurveySubmission submission = surveySubmissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("提交记录不存在");
        }
        surveyAnswerMapper.delete(new LambdaQueryWrapper<SurveyAnswer>()
                .eq(SurveyAnswer::getSubmissionId, submissionId));
        surveySubmissionMapper.deleteById(submissionId);
    }

    @Override
    public List<SurveyDetailDTO> listStudentSurveys(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return surveyMapper.selectList(new LambdaQueryWrapper<Survey>()
                        .eq(Survey::getStatus, 1)
                        .and(wrapper -> wrapper.isNull(Survey::getStartTime).or().le(Survey::getStartTime, now))
                        .and(wrapper -> wrapper.isNull(Survey::getEndTime).or().ge(Survey::getEndTime, now))
                        .orderByDesc(Survey::getPublishedAt)
                        .orderByDesc(Survey::getCreatedAt))
                .stream()
                .filter(survey -> findSubmission(survey.getId(), userId) == null)
                .map(survey -> getDetail(survey.getId(), userId))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void submit(Long surveyId, Long userId, SurveySubmitRequest request) {
        Survey survey = requireSurvey(surveyId);
        LocalDateTime now = LocalDateTime.now();
        if (!Integer.valueOf(1).equals(survey.getStatus())
                || (survey.getStartTime() != null && survey.getStartTime().isAfter(now))
                || (survey.getEndTime() != null && survey.getEndTime().isBefore(now))) {
            throw new IllegalStateException("问卷未开放或已结束，不能提交");
        }
        if (findSubmission(surveyId, userId) != null) {
            throw new IllegalStateException("该问卷已提交，不能重复提交");
        }

        List<SurveyQuestion> questions = listQuestions(surveyId);
        Map<Long, SurveyAnswerRequest> answerMap = buildAnswerMap(request);
        validateAnswers(questions, answerMap);

        SurveySubmission submission = new SurveySubmission();
        submission.setSurveyId(surveyId);
        submission.setUserId(userId);
        submission.setSubmitTime(now);
        submission.setCreatedAt(now);
        surveySubmissionMapper.insert(submission);

        for (SurveyQuestion question : questions) {
            SurveyAnswerRequest answerRequest = answerMap.get(question.getId());
            SurveyAnswer answer = new SurveyAnswer();
            answer.setSubmissionId(submission.getId());
            answer.setSurveyId(surveyId);
            answer.setQuestionId(question.getId());
            answer.setUserId(userId);
            answer.setQuestionCode(question.getQuestionCode());
            answer.setIndicatorName(question.getIndicatorName());
            answer.setCreatedAt(now);
            if (Integer.valueOf(QUESTION_TYPE_SCALE).equals(question.getQuestionType())) {
                answer.setNumericAnswer(answerRequest.getNumericAnswer());
            } else if (answerRequest != null && StringUtils.hasText(answerRequest.getTextAnswer())) {
                answer.setTextAnswer(answerRequest.getTextAnswer().trim());
            }
            surveyAnswerMapper.insert(answer);
        }
    }

    @Override
    public List<SurveySubmissionDTO> listSubmissions(Long surveyId) {
        requireSurvey(surveyId);
        Map<Long, SurveyQuestion> questionMap = listQuestions(surveyId).stream()
                .collect(Collectors.toMap(SurveyQuestion::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<SurveySubmission> submissions = surveySubmissionMapper.selectList(new LambdaQueryWrapper<SurveySubmission>()
                .eq(SurveySubmission::getSurveyId, surveyId)
                .orderByDesc(SurveySubmission::getSubmitTime));
        if (submissions.isEmpty()) {
            return List.of();
        }

        Map<Long, SysUser> userMap = new HashMap<>();
        for (SurveySubmission submission : submissions) {
            SysUser user = sysUserService.getById(submission.getUserId());
            if (user != null) {
                userMap.put(submission.getUserId(), user);
            }
        }

        List<Long> submissionIds = submissions.stream().map(SurveySubmission::getId).toList();
        List<SurveyAnswer> answers = surveyAnswerMapper.selectList(new LambdaQueryWrapper<SurveyAnswer>()
                .in(SurveyAnswer::getSubmissionId, submissionIds));
        Map<Long, List<SurveyAnswer>> answersBySubmission = answers.stream()
                .collect(Collectors.groupingBy(SurveyAnswer::getSubmissionId));

        return submissions.stream().map(submission -> {
            SurveySubmissionDTO dto = new SurveySubmissionDTO();
            dto.setId(submission.getId());
            dto.setSurveyId(submission.getSurveyId());
            dto.setUserId(submission.getUserId());
            dto.setSubmitTime(submission.getSubmitTime());
            SysUser user = userMap.get(submission.getUserId());
            if (user != null) {
                dto.setUsername(user.getUsername());
                dto.setRealName(user.getRealName());
            }
            dto.setAnswers(answersBySubmission.getOrDefault(submission.getId(), List.of()).stream()
                    .map(answer -> toSubmissionAnswerDTO(answer, questionMap.get(answer.getQuestionId())))
                    .toList());
            return dto;
        }).toList();
    }

    @Override
    public SurveyTrendDTO getStudentTrend(Long userId) {
        SysUser user = userId == null ? null : sysUserService.getById(userId);
        SurveyTrendDTO dto = new SurveyTrendDTO();
        dto.setStudentId(userId);
        if (user != null) {
            dto.setUsername(user.getUsername());
            dto.setRealName(user.getRealName());
        }

        List<SurveyAnswer> answers = surveyAnswerMapper.selectList(new LambdaQueryWrapper<SurveyAnswer>()
                .eq(SurveyAnswer::getUserId, userId)
                .isNotNull(SurveyAnswer::getNumericAnswer)
                .orderByAsc(SurveyAnswer::getCreatedAt));
        if (answers.isEmpty()) {
            return dto;
        }

        Map<Long, Survey> surveyMap = surveyMapper.selectBatchIds(answers.stream()
                        .map(SurveyAnswer::getSurveyId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Survey::getId, Function.identity(), (a, b) -> a));
        Map<Long, SurveyQuestion> questionMap = surveyQuestionMapper.selectBatchIds(answers.stream()
                        .map(SurveyAnswer::getQuestionId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(SurveyQuestion::getId, Function.identity(), (a, b) -> a));

        Map<String, SurveyTrendDTO.Series> seriesMap = new LinkedHashMap<>();
        for (SurveyAnswer answer : answers) {
            SurveyQuestion question = questionMap.get(answer.getQuestionId());
            String code = firstText(answer.getQuestionCode(), question == null ? null : question.getQuestionCode(), "question-" + answer.getQuestionId());
            SurveyTrendDTO.Series series = seriesMap.computeIfAbsent(code, key -> {
                SurveyTrendDTO.Series created = new SurveyTrendDTO.Series();
                created.setQuestionCode(key);
                created.setIndicatorName(firstText(answer.getIndicatorName(), question == null ? null : question.getIndicatorName(), key));
                created.setQuestionText(question == null ? key : question.getQuestionText());
                return created;
            });
            Survey survey = surveyMap.get(answer.getSurveyId());
            SurveyTrendDTO.Point point = new SurveyTrendDTO.Point();
            point.setSurveyId(answer.getSurveyId());
            point.setSurveyTitle(survey == null ? "" : survey.getTitle());
            point.setAcademicYear(survey == null ? null : survey.getAcademicYear());
            point.setTermNo(survey == null ? null : survey.getTermNo());
            point.setSubmitTime(answer.getCreatedAt());
            point.setValue(answer.getNumericAnswer());
            series.getPoints().add(point);
        }
        dto.setSeries(new ArrayList<>(seriesMap.values()));
        return dto;
    }

    private void validateTemplateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("问卷模板不能为空");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("模板文件名不能为空");
        }
        String lowerName = filename.toLowerCase();
        if (!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xls")) {
            throw new IllegalArgumentException("问卷模板仅支持 xlsx、xls 文件");
        }
    }

    private List<QuestionDraft> readQuestionDrafts(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<QuestionDraft> rowQuestions = readRowQuestionDrafts(sheet);
            Row header = sheet == null ? null : sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                return rowQuestions;
            }
            List<QuestionDraft> questions = new ArrayList<>();
            short lastCellNum = header.getLastCellNum();
            for (int cellIndex = SKIP_EXPORT_COLUMNS; cellIndex < lastCellNum; cellIndex++) {
                Cell cell = header.getCell(cellIndex);
                String rawText = cell == null ? "" : dataFormatter.formatCellValue(cell);
                String text = cleanQuestionText(rawText);
                if (StringUtils.hasText(text)) {
                    questions.add(newDraft(text, questions.size() + 1));
                }
            }
            return questions.isEmpty() ? rowQuestions : questions;
        } catch (Exception e) {
            throw new IllegalArgumentException("问卷模板解析失败: " + e.getMessage(), e);
        }
    }

    private List<QuestionDraft> readRowQuestionDrafts(Sheet sheet) {
        if (sheet == null) {
            return List.of();
        }
        Map<String, QuestionDraft> uniqueQuestions = new LinkedHashMap<>();
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String bestQuestion = "";
            for (int cellIndex = Math.max(row.getFirstCellNum(), 0); cellIndex < row.getLastCellNum(); cellIndex++) {
                Cell cell = row.getCell(cellIndex);
                String rawText = cell == null ? "" : dataFormatter.formatCellValue(cell);
                String text = cleanQuestionText(rawText);
                if (isQuestionCandidate(text) && text.length() > bestQuestion.length()) {
                    bestQuestion = text;
                }
            }
            if (StringUtils.hasText(bestQuestion)) {
                uniqueQuestions.putIfAbsent(bestQuestion, newDraft(bestQuestion, uniqueQuestions.size() + 1));
            }
        }
        return new ArrayList<>(uniqueQuestions.values());
    }

    private QuestionDraft newDraft(String questionText, int questionNo) {
        boolean textQuestion = questionText.contains("建议") || questionText.contains("说明") || questionText.contains("期待");
        String code = "q_" + Integer.toHexString(questionText.trim().hashCode()).replace("-", "n");
        String indicator = questionText.length() > 24 ? questionText.substring(0, 24) : questionText;
        return new QuestionDraft(code, indicator, questionText, textQuestion ? QUESTION_TYPE_TEXT : QUESTION_TYPE_SCALE, textQuestion ? 0 : 1);
    }

    private boolean isQuestionCandidate(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.trim();
        if (normalized.length() < 4 || normalized.length() > 1000) {
            return false;
        }
        if (normalized.matches("^\\d+(\\.0)?$")) {
            return false;
        }
        String lower = normalized.toLowerCase();
        if (lower.matches("^(id|no|name|username|student|class|phone|time|score|submit)$")) {
            return false;
        }
        return !isScaleOptionText(normalized);
    }

    private boolean isScaleOptionText(String text) {
        return "完全符合".equals(text)
                || "比较符合".equals(text)
                || "一般符合".equals(text)
                || "比较不符合".equals(text)
                || "不符合".equals(text)
                || "非常满意".equals(text)
                || "满意".equals(text)
                || "一般".equals(text)
                || "不满意".equals(text)
                || "非常不满意".equals(text);
    }

    private String cleanQuestionText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim()
                .replace("（必填）", "")
                .replace("(必填)", "")
                .trim();
    }

    private String stripExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "未命名问卷";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private Survey requireSurvey(Long surveyId) {
        if (surveyId == null) {
            throw new IllegalArgumentException("缺少问卷ID");
        }
        Survey survey = surveyMapper.selectById(surveyId);
        if (survey == null) {
            throw new IllegalArgumentException("问卷不存在");
        }
        return survey;
    }

    private List<SurveyTemplateQuestion> listTemplateQuestions(Long templateId) {
        return surveyTemplateQuestionMapper.selectList(new LambdaQueryWrapper<SurveyTemplateQuestion>()
                .eq(SurveyTemplateQuestion::getTemplateId, templateId)
                .orderByAsc(SurveyTemplateQuestion::getSortOrder)
                .orderByAsc(SurveyTemplateQuestion::getQuestionNo));
    }

    private List<SurveyQuestion> listQuestions(Long surveyId) {
        return surveyQuestionMapper.selectList(new LambdaQueryWrapper<SurveyQuestion>()
                .eq(SurveyQuestion::getSurveyId, surveyId)
                .orderByAsc(SurveyQuestion::getSortOrder)
                .orderByAsc(SurveyQuestion::getQuestionNo));
    }

    private SurveySubmission findSubmission(Long surveyId, Long userId) {
        if (surveyId == null || userId == null) {
            return null;
        }
        return surveySubmissionMapper.selectOne(new LambdaQueryWrapper<SurveySubmission>()
                .eq(SurveySubmission::getSurveyId, surveyId)
                .eq(SurveySubmission::getUserId, userId)
                .last("LIMIT 1"));
    }

    private Map<Long, SurveyAnswerRequest> buildAnswerMap(SurveySubmitRequest request) {
        if (request == null || request.getAnswers() == null) {
            return Map.of();
        }
        return request.getAnswers().stream()
                .filter(answer -> answer.getQuestionId() != null)
                .collect(Collectors.toMap(SurveyAnswerRequest::getQuestionId, Function.identity(), (first, second) -> second));
    }

    private void validateAnswers(List<SurveyQuestion> questions, Map<Long, SurveyAnswerRequest> answerMap) {
        for (SurveyQuestion question : questions) {
            SurveyAnswerRequest answer = answerMap.get(question.getId());
            if (Integer.valueOf(QUESTION_TYPE_SCALE).equals(question.getQuestionType())) {
                if (answer == null || answer.getNumericAnswer() == null) {
                    throw new IllegalArgumentException("第 " + question.getQuestionNo() + " 题为必填题");
                }
                int value = answer.getNumericAnswer();
                if (value < 1 || value > 5) {
                    throw new IllegalArgumentException("第 " + question.getQuestionNo() + " 题答案必须在 1-5 之间");
                }
            } else if (Integer.valueOf(1).equals(question.getRequired())) {
                if (answer == null || !StringUtils.hasText(answer.getTextAnswer())) {
                    throw new IllegalArgumentException("第 " + question.getQuestionNo() + " 题为必填题");
                }
            }
        }
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("结束时间不能早于开始时间");
        }
    }

    private SurveyQuestionDTO toQuestionDTO(SurveyQuestion question) {
        SurveyQuestionDTO dto = new SurveyQuestionDTO();
        dto.setId(question.getId());
        dto.setQuestionNo(question.getQuestionNo());
        dto.setQuestionCode(question.getQuestionCode());
        dto.setIndicatorName(question.getIndicatorName());
        dto.setQuestionText(question.getQuestionText());
        dto.setQuestionType(question.getQuestionType());
        dto.setRequired(question.getRequired());
        dto.setSortOrder(question.getSortOrder());
        return dto;
    }

    private SurveySubmissionAnswerDTO toSubmissionAnswerDTO(SurveyAnswer answer, SurveyQuestion question) {
        SurveySubmissionAnswerDTO dto = new SurveySubmissionAnswerDTO();
        dto.setQuestionId(answer.getQuestionId());
        dto.setQuestionCode(firstText(answer.getQuestionCode(), question == null ? null : question.getQuestionCode(), null));
        dto.setIndicatorName(firstText(answer.getIndicatorName(), question == null ? null : question.getIndicatorName(), null));
        dto.setNumericAnswer(answer.getNumericAnswer());
        dto.setTextAnswer(answer.getTextAnswer());
        if (question != null) {
            dto.setQuestionNo(question.getQuestionNo());
            dto.setQuestionText(question.getQuestionText());
            dto.setQuestionType(question.getQuestionType());
        }
        return dto;
    }

    private String firstText(String first, String second, String fallback) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        if (StringUtils.hasText(second)) {
            return second;
        }
        return fallback;
    }

    private record QuestionDraft(String questionCode, String indicatorName, String questionText, Integer questionType, Integer required) {
    }
}
