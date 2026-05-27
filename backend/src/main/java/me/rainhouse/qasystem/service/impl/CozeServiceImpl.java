package me.rainhouse.qasystem.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.service.CozeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class CozeServiceImpl implements CozeService {

    private static final String DEFAULT_ERROR_REPLY = "抱歉，AI 助手暂时开小差啦，请稍后再试～";
    private static final String NOT_CONFIGURED_REPLY = "系统暂未配置 AI 接口，请联系管理员。";

    @Value("${coze.api.token:}")
    private String apiToken;

    @Value("${coze.workflow.api.url:https://api.coze.cn/v1/workflow/run}")
    private String workflowApiUrl;

    @Value("${coze.workflow.inquiry-intent-recognition.id:7642303573099675684}")
    private String inquiryIntentRecognitionWorkflowId;

    @Value("${coze.workflow.unknown-business-exception.id:7643309248310673443}")
    private String unknownBusinessExceptionWorkflowId;

    @Value("${coze.workflow.custom-learning-resources.id:7644189104666247178}")
    private String customLearningResourcesWorkflowId;

    @Value("${coze.workflow.psychological-counseling.id:7644210479727935528}")
    private String psychologicalCounselingWorkflowId;

    @Value("${coze.bot.id:}")
    private String botId;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String chat(String userId, String query) {
        Map<String, Object> inquiryParams = new HashMap<>();
        inquiryParams.put("query", query);

        JsonNode inquiryOutput = runWorkflowForOutput(inquiryIntentRecognitionWorkflowId, inquiryParams);
        String inquiryAnswer = extractReadableAnswer(inquiryOutput);

        if (StringUtils.hasText(inquiryAnswer) && !isUnresolvedAnswer(inquiryOutput, inquiryAnswer)) {
            return inquiryAnswer;
        }

        log.info("工作流1未命中，自动转入工作流2: userId={}, query={}", userId, query);
        Map<String, Object> fallbackParams = new HashMap<>();
        fallbackParams.put("unresolved_query", query);
        JsonNode fallbackOutput = runWorkflowForOutput(unknownBusinessExceptionWorkflowId, fallbackParams);
        return extractManualFallbackAnswer(fallbackOutput, query);
    }

    @Override
    public String generateLearningResources(String studentId,
                                            String weakKnowledge,
                                            String warningLevel,
                                            String surveyIndicator) {
        Map<String, Object> params = new HashMap<>();
        params.put("student_id", studentId);
        params.put("weak_knowledge", weakKnowledge);
        params.put("warning_level", warningLevel);
        params.put("survey_indicator", surveyIndicator);

        JsonNode output = runWorkflowForOutput(customLearningResourcesWorkflowId, params);
        String studyPlan = findText(output, "study_plan", "help_plan", "reply_text", "answer", "output");
        return StringUtils.hasText(studyPlan) ? studyPlan : extractReadableAnswer(output);
    }

    @Override
    public String psychologicalCounseling(String studentId, String studentMsg) {
        Map<String, Object> params = new HashMap<>();
        params.put("student_id", studentId);
        params.put("student_msg", studentMsg);

        JsonNode output = runWorkflowForOutput(psychologicalCounselingWorkflowId, params);
        String replyText = findText(output, "reply_text", "answer", "output", "content");
        String stressLevel = findText(output, "stress_level");

        if (!StringUtils.hasText(replyText)) {
            return extractReadableAnswer(output);
        }
        if (!StringUtils.hasText(stressLevel)) {
            return replyText;
        }
        return replyText + "\n\n心理压力等级：" + stressLevel;
    }

    private JsonNode runWorkflowForOutput(String workflowId, Map<String, Object> parameters) {
        if (!StringUtils.hasText(apiToken)) {
            log.warn("Coze API Token 未配置！");
            return TextNode.valueOf(NOT_CONFIGURED_REPLY);
        }
        if (!StringUtils.hasText(workflowId)) {
            log.warn("Coze Workflow ID 未配置！");
            return TextNode.valueOf(NOT_CONFIGURED_REPLY);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiToken);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("workflow_id", workflowId);
            requestBody.put("parameters", parameters);
            if (StringUtils.hasText(botId)) {
                requestBody.put("bot_id", botId);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            log.info("请求 Coze 工作流: url={}, workflowId={}, parameters={}", workflowApiUrl, workflowId, parameters.keySet());

            ResponseEntity<String> response = restTemplate.postForEntity(workflowApiUrl, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Coze 工作流 HTTP 状态码异常: {}", response.getStatusCode());
                return TextNode.valueOf(DEFAULT_ERROR_REPLY);
            }

            JsonNode root = mapper.readTree(response.getBody());
            if (root.has("code") && root.get("code").asInt() != 0) {
                String errorMsg = root.has("msg") ? root.get("msg").asText() : "未知错误";
                log.error("Coze 工作流返回错误: {}", errorMsg);
                return TextNode.valueOf(DEFAULT_ERROR_REPLY);
            }

            if (root.has("data")) {
                return normalizeJsonNode(root.get("data"));
            }
            return normalizeJsonNode(root);
        } catch (Exception e) {
            log.error("调用 Coze 工作流失败", e);
            return TextNode.valueOf(DEFAULT_ERROR_REPLY);
        }
    }

    private JsonNode normalizeJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return TextNode.valueOf("");
        }

        if (node.isTextual()) {
            String text = node.asText();
            if (!StringUtils.hasText(text)) {
                return TextNode.valueOf("");
            }
            try {
                return normalizeJsonNode(mapper.readTree(text));
            } catch (Exception ignored) {
                return TextNode.valueOf(text);
            }
        }

        String nested = findText(node, "output", "content", "reply_text", "study_plan");
        if (StringUtils.hasText(nested) && looksLikeJson(nested)) {
            try {
                return normalizeJsonNode(mapper.readTree(nested));
            } catch (Exception ignored) {
                return node;
            }
        }

        return node;
    }

    private String extractReadableAnswer(JsonNode output) {
        if (output == null || output.isNull()) {
            return DEFAULT_ERROR_REPLY;
        }
        if (output.isTextual()) {
            return output.asText();
        }

        String answer = findText(output, "reply_text", "answer", "result", "text", "content", "output", "study_plan", "status");
        if (StringUtils.hasText(answer)) {
            return answer;
        }
        return output.toString();
    }

    private String extractManualFallbackAnswer(JsonNode output, String query) {
        String replyText = findText(output, "reply_text", "answer", "message", "output", "content");
        if (StringUtils.hasText(replyText) && !looksLikeJson(replyText)) {
            return replyText;
        }

        String action = findText(output, "action");
        String teacher = findText(output, "teacher");
        String phone = findText(output, "phone");
        String category = parseCategory(findText(output, "category"));

        if (StringUtils.hasText(action) || StringUtils.hasText(teacher) || StringUtils.hasText(phone)) {
            StringBuilder builder = new StringBuilder("暂未在知识库中找到完全匹配的问题，可转人工进一步处理。");
            if (StringUtils.hasText(category)) {
                builder.append("\n问题分类：").append(category);
            }
            if (StringUtils.hasText(teacher)) {
                builder.append("\n负责老师：").append(teacher);
            }
            if (StringUtils.hasText(phone)) {
                builder.append("\n联系电话：").append(phone);
            }
            if ("SHOW_MANUAL_BUTTON".equalsIgnoreCase(action)) {
                builder.append("\n您也可以点击“转人工”继续咨询。");
            }
            return builder.toString();
        }

        return "暂未找到与“" + query + "”完全匹配的答案，建议转人工或稍后由管理员补充知识库。";
    }

    private boolean isUnresolvedAnswer(JsonNode output, String answer) {
        String status = findText(output, "status");
        String action = findText(output, "action");
        String combined = (answer + " " + status + " " + action + " " + output.toString()).toLowerCase(Locale.ROOT);

        return combined.contains("未找到")
                || combined.contains("未检索到")
                || combined.contains("未查询到")
                || combined.contains("没有找到")
                || combined.contains("没有相关")
                || combined.contains("无相关")
                || combined.contains("暂无相关")
                || combined.contains("找不到")
                || combined.contains("无法回答")
                || combined.contains("无法识别")
                || combined.contains("不知道")
                || combined.contains("不清楚")
                || combined.contains("no relevant")
                || combined.contains("not found")
                || combined.contains("unknown")
                || combined.contains("unresolved")
                || combined.contains("SHOW_MANUAL_BUTTON".toLowerCase(Locale.ROOT));
    }

    private String findText(JsonNode node, String... fieldNames) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }

        for (String fieldName : fieldNames) {
            JsonNode found = node.findValue(fieldName);
            if (found == null || found.isNull()) {
                continue;
            }
            if (found.isTextual()) {
                return found.asText();
            }
            if (found.isNumber() || found.isBoolean()) {
                return found.asText();
            }
            return found.toString();
        }

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String value = findText(entry.getValue(), fieldNames);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = findText(item, fieldNames);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }

        return "";
    }

    private String parseCategory(String categoryText) {
        if (!StringUtils.hasText(categoryText)) {
            return "";
        }
        if (!looksLikeJson(categoryText)) {
            return categoryText;
        }
        try {
            JsonNode categoryNode = mapper.readTree(categoryText);
            String category = findText(categoryNode, "category", "name");
            return StringUtils.hasText(category) ? category : categoryText;
        } catch (Exception ignored) {
            return categoryText;
        }
    }

    private boolean looksLikeJson(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String trimmed = text.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
}
