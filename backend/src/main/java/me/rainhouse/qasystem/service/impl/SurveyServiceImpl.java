package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.common.dto.survey.SurveyAnswerRequest;
import me.rainhouse.qasystem.common.dto.survey.SurveyDetailDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveyQuestionDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveySubmissionAnswerDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveySubmissionDTO;
import me.rainhouse.qasystem.common.dto.survey.SurveySubmitRequest;
import me.rainhouse.qasystem.entity.Survey;
import me.rainhouse.qasystem.entity.SurveyAnswer;
import me.rainhouse.qasystem.entity.SurveyQuestion;
import me.rainhouse.qasystem.entity.SurveySubmission;
import me.rainhouse.qasystem.entity.SysUser;
import me.rainhouse.qasystem.mapper.SurveyAnswerMapper;
import me.rainhouse.qasystem.mapper.SurveyMapper;
import me.rainhouse.qasystem.mapper.SurveyQuestionMapper;
import me.rainhouse.qasystem.mapper.SurveySubmissionMapper;
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
    private final SysUserService sysUserService;
    private final DataFormatter dataFormatter = new DataFormatter();

    public SurveyServiceImpl(SurveyMapper surveyMapper,
                             SurveyQuestionMapper surveyQuestionMapper,
                             SurveySubmissionMapper surveySubmissionMapper,
                             SurveyAnswerMapper surveyAnswerMapper,
                             SysUserService sysUserService) {
        this.surveyMapper = surveyMapper;
        this.surveyQuestionMapper = surveyQuestionMapper;
        this.surveySubmissionMapper = surveySubmissionMapper;
        this.surveyAnswerMapper = surveyAnswerMapper;
        this.sysUserService = sysUserService;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Survey importTemplate(MultipartFile file, String title, String description, Long adminId) {
        validateTemplateFile(file);
        List<String> questionTexts = readQuestions(file);
        if (questionTexts.isEmpty()) {
            throw new IllegalArgumentException("模板中未读取到问卷题目");
        }

        Survey survey = new Survey();
        survey.setTitle(StringUtils.hasText(title) ? title.trim() : stripExtension(file.getOriginalFilename()));
        survey.setDescription(StringUtils.hasText(description) ? description.trim() : null);
        survey.setStatus(0);
        survey.setScopeType("ALL");
        survey.setCreatedBy(adminId);
        survey.setCreatedAt(LocalDateTime.now());
        survey.setUpdatedAt(LocalDateTime.now());
        surveyMapper.insert(survey);

        List<SurveyQuestion> questions = new ArrayList<>();
        for (int i = 0; i < questionTexts.size(); i++) {
            boolean isLast = i == questionTexts.size() - 1;
            SurveyQuestion question = new SurveyQuestion();
            question.setSurveyId(survey.getId());
            question.setQuestionNo(i + 1);
            question.setQuestionText(questionTexts.get(i));
            question.setQuestionType(isLast ? QUESTION_TYPE_TEXT : QUESTION_TYPE_SCALE);
            question.setRequired(isLast ? 0 : 1);
            question.setSortOrder(i + 1);
            question.setCreatedAt(LocalDateTime.now());
            questions.add(question);
        }
        questions.forEach(surveyQuestionMapper::insert);
        log.info("问卷模板导入完成: surveyId={}, questions={}", survey.getId(), questions.size());
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
        List<SurveyQuestion> questions = listQuestions(surveyId);
        dto.setQuestions(questions.stream().map(this::toQuestionDTO).toList());

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

    @Override
    public List<SurveyDetailDTO> listStudentSurveys(Long userId) {
        return surveyMapper.selectList(new LambdaQueryWrapper<Survey>()
                        .eq(Survey::getStatus, 1)
                        .orderByDesc(Survey::getPublishedAt)
                        .orderByDesc(Survey::getCreatedAt))
                .stream()
                .map(survey -> getDetail(survey.getId(), userId))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void submit(Long surveyId, Long userId, SurveySubmitRequest request) {
        Survey survey = requireSurvey(surveyId);
        if (!Integer.valueOf(1).equals(survey.getStatus())) {
            throw new IllegalStateException("问卷未发布或已关闭，不能提交");
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
        submission.setSubmitTime(LocalDateTime.now());
        submission.setCreatedAt(LocalDateTime.now());
        surveySubmissionMapper.insert(submission);

        for (SurveyQuestion question : questions) {
            SurveyAnswerRequest answerRequest = answerMap.get(question.getId());
            SurveyAnswer answer = new SurveyAnswer();
            answer.setSubmissionId(submission.getId());
            answer.setSurveyId(surveyId);
            answer.setQuestionId(question.getId());
            answer.setUserId(userId);
            answer.setCreatedAt(LocalDateTime.now());
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
        List<SurveyQuestion> questions = listQuestions(surveyId);
        Map<Long, SurveyQuestion> questionMap = questions.stream()
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
            dto.setAnswers((answersBySubmission.getOrDefault(submission.getId(), List.of())).stream()
                    .map(answer -> toSubmissionAnswerDTO(answer, questionMap.get(answer.getQuestionId())))
                    .toList());
            return dto;
        }).toList();
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

    private List<String> readQuestions(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                return List.of();
            }
            List<String> questions = new ArrayList<>();
            short lastCellNum = header.getLastCellNum();
            for (int cellIndex = SKIP_EXPORT_COLUMNS; cellIndex < lastCellNum; cellIndex++) {
                Cell cell = header.getCell(cellIndex);
                String rawText = cell == null ? "" : dataFormatter.formatCellValue(cell);
                String text = cleanQuestionText(rawText);
                if (StringUtils.hasText(text)) {
                    questions.add(text);
                }
            }
            return questions;
        } catch (Exception e) {
            throw new IllegalArgumentException("问卷模板解析失败: " + e.getMessage(), e);
        }
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

    private SurveyQuestionDTO toQuestionDTO(SurveyQuestion question) {
        SurveyQuestionDTO dto = new SurveyQuestionDTO();
        dto.setId(question.getId());
        dto.setQuestionNo(question.getQuestionNo());
        dto.setQuestionText(question.getQuestionText());
        dto.setQuestionType(question.getQuestionType());
        dto.setRequired(question.getRequired());
        dto.setSortOrder(question.getSortOrder());
        return dto;
    }

    private SurveySubmissionAnswerDTO toSubmissionAnswerDTO(SurveyAnswer answer, SurveyQuestion question) {
        SurveySubmissionAnswerDTO dto = new SurveySubmissionAnswerDTO();
        dto.setQuestionId(answer.getQuestionId());
        dto.setNumericAnswer(answer.getNumericAnswer());
        dto.setTextAnswer(answer.getTextAnswer());
        if (question != null) {
            dto.setQuestionNo(question.getQuestionNo());
            dto.setQuestionText(question.getQuestionText());
            dto.setQuestionType(question.getQuestionType());
        }
        return dto;
    }
}
