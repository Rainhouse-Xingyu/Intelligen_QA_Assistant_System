package me.rainhouse.qasystem.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.service.CozeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class CozeServiceImpl implements CozeService {

    @Value("${coze.api.url}")
    private String apiUrl;

    @Value("${coze.api.token}")
    private String apiToken;

    @Value("${coze.bot.id}")
    private String botId;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public String chat(String userId, String query) {
        if (apiToken == null || apiToken.isEmpty() || botId == null || botId.isEmpty()) {
            log.warn("Coze API Token 或 Bot ID 未配置！");
            return "系统暂未配置 AI 接口，请联系管理员。";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiToken);
            // Coze 可能会要求一些其他的 Header，v2 版本基本就是 Authorization

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("bot_id", botId);
            requestBody.put("user", userId); // Coze 根据 user 字段独立维护会话历史
            requestBody.put("query", query);
            requestBody.put("stream", false);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("请求 Coze 接口: url={}, userId={}, query={}", apiUrl, userId, query);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());

                // Coze v2 HTTP 响应解析
                // 成功时返回 code: 0
                if (root.has("code") && root.get("code").asInt() == 0) {
                    JsonNode messages = root.get("messages");
                    if (messages != null && messages.isArray()) {
                        for (JsonNode msgNode : messages) {
                            // 提取 type = "answer" 的文本
                            if ("answer".equals(msgNode.get("type").asText())) {
                                return msgNode.get("content").asText();
                            }
                        }
                    }
                } else {
                    String errorMsg = root.has("msg") ? root.get("msg").asText() : "未知错误";
                    log.error("Coze API 返回错误: {}", errorMsg);
                }
            } else {
                log.error("Coze API HTTP 状态码异常: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("调用 Coze 接口失败", e);
        }

        return "抱歉，AI 助手暂时开小差啦，请稍后再试～";
    }
}