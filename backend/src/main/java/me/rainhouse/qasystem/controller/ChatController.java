package me.rainhouse.qasystem.controller;

import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.common.dto.AiChatRequest;
import me.rainhouse.qasystem.common.dto.AiChatResponse;
import me.rainhouse.qasystem.common.utils.CasualConversationUtils;
import me.rainhouse.qasystem.entity.ChatMessage;
import me.rainhouse.qasystem.entity.ChatSession;
import me.rainhouse.qasystem.service.AiChatService;
import me.rainhouse.qasystem.service.AudioService;
import me.rainhouse.qasystem.service.BizContactService;
import me.rainhouse.qasystem.service.ChatMessageService;
import me.rainhouse.qasystem.service.ChatSessionService;
import me.rainhouse.qasystem.service.StatHotQuestionService;
import me.rainhouse.qasystem.service.UnrecognizedQueryService;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
import me.rainhouse.qasystem.websocket.ChatWebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private AudioService audioService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private BizContactService bizContactService;

    @Autowired
    private StatHotQuestionService statHotQuestionService;

    @Autowired
    private UnrecognizedQueryService unrecognizedQueryService;

    @Autowired
    private LocalModelClient localModelClient;

    // 从请求 attributes 中获取 userId (由 AuthInterceptor 解析 JWT 后放入)
    private Long getUserIdOpt(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj != null) {
            return Long.valueOf(userIdObj.toString());
        }
        return 0L; // Fallback mock userId for unauthenticated calls if allowed
    }

    /**
     * 发送文本消息
     */
    @PostMapping("/text")
    public Result<String> sendText(@RequestParam("query") String query,
                                   @RequestParam(value = "moduleType", required = false) String moduleType,
                                   @RequestParam(value = "needTts", defaultValue = "false") Boolean needTts,
                                   HttpServletRequest request) {
        
        // 埋点统计（4.1 模块）：纯寒暄不进入热词榜
        if (!CasualConversationUtils.isCasualOnly(query)) {
            statHotQuestionService.recordQuestion(query);
        }

        Long userId = getUserIdOpt(request);
        ChatSession session = chatSessionService.getOrCreateActiveSession(userId);

        // 如果处于人工状态，将消息发给人工即可，不走AI
        if (session.getStatus() == 1) {
            saveMessage(session.getId(), 1, 1, query, null);
            // 通过 WS 通知客服
            String wsAdminMsg = String.format("{\"type\":\"USER_MSG\", \"sessionId\":%d, \"userId\":%d, \"content\":\"%s\"}", session.getId(), userId, query.replace("\"", "\\\""));
            if (session.getAdminId() != null) {
                ChatWebSocketServer.sendMessageToAdmin(String.valueOf(session.getAdminId()), wsAdminMsg);
            }
            return Result.success("消息已转交人工客服");
        }

        // 1. 保存用户的文本问题
        saveMessage(session.getId(), 1, 1, query, null);

        // 2. 进入智能问答核心模块：问题改写、意图分类、向量检索、重排、答案生成
        AiChatResponse aiChatResponse = aiChatService.chat(userId, session.getId(), query, moduleType);
        String aiAnswer = aiChatResponse.getAnswer();

        // 【2.4模块】业务联动自动推送 API：检查关键字并追加教务老师联系方式
        aiAnswer = bizContactService.appendContactInfoIfMatch(query, aiAnswer);

        // 3. 将 AI 答案存入库
        String mediaUrl = null;
        
        // 4. (2.2模块) 判断是否需要播报语音
        if (needTts) {
            mediaUrl = audioService.textToSpeech(aiAnswer);
        }
        saveMessage(session.getId(), 2, needTts ? 2 : 1, aiAnswer, mediaUrl);
        updateAnswerSource(session.getId(), aiChatResponse.getAnswerSource());

        return Result.success(aiAnswer);
    }

    @PostMapping("/text-detail")
    public Result<AiChatResponse> sendTextDetail(@RequestParam("query") String query,
                                                 @RequestParam(value = "moduleType", required = false) String moduleType,
                                                 HttpServletRequest request) {
        if (!CasualConversationUtils.isCasualOnly(query)) {
            statHotQuestionService.recordQuestion(query);
        }

        Long userId = getUserIdOpt(request);
        ChatSession session = chatSessionService.getOrCreateActiveSession(userId);
        if (session.getStatus() == 1) {
            saveMessage(session.getId(), 1, 1, query, null);
            String wsAdminMsg = String.format("{\"type\":\"USER_MSG\", \"sessionId\":%d, \"userId\":%d, \"content\":\"%s\"}", session.getId(), userId, query.replace("\"", "\\\""));
            if (session.getAdminId() != null) {
                ChatWebSocketServer.sendMessageToAdmin(String.valueOf(session.getAdminId()), wsAdminMsg);
            }
            AiChatResponse response = AiChatResponse.builder()
                    .originalQuestion(query)
                    .rewriteQuestion(query)
                    .moduleType(moduleType)
                    .hitStatus(0)
                    .hitLabel("转人工")
                    .topScore(0.0)
                    .answer("消息已转交人工客服")
                    .answerSource("HUMAN_TRANSFER")
                    .responseTimeMs(0L)
                    .references(List.of())
                    .build();
            return Result.success(response);
        }

        saveMessage(session.getId(), 1, 1, query, null);
        AiChatResponse response = aiChatService.chat(userId, session.getId(), query, moduleType);
        String aiAnswer = bizContactService.appendContactInfoIfMatch(query, response.getAnswer());
        response.setAnswer(aiAnswer);
        saveMessage(session.getId(), 2, 1, aiAnswer, null);
        updateAnswerSource(session.getId(), response.getAnswerSource());
        return Result.success(response);
    }

    /**
     * 智能问答核心模块调试接口，返回改写、分类、命中和引用详情。
     */
    @PostMapping("/ai-core")
    public Result<AiChatResponse> aiCore(@RequestBody AiChatRequest aiChatRequest,
                                         HttpServletRequest request) {
        Long userId = getUserIdOpt(request);
        ChatSession session = chatSessionService.getOrCreateActiveSession(userId);
        AiChatResponse response = aiChatService.chat(
                userId,
                session.getId(),
                aiChatRequest.getQuery(),
                aiChatRequest.getModuleType()
        );
        updateAnswerSource(session.getId(), response.getAnswerSource());
        return Result.success(response);
    }

    /**
     * 智能问答核心模块 SSE 接口，按 metadata、answer、done 事件流式返回。
     */
    @PostMapping(value = "/ai-core/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiCoreStream(@RequestBody AiChatRequest aiChatRequest,
                                   HttpServletRequest request) {
        Long userId = getUserIdOpt(request);
        ChatSession session = chatSessionService.getOrCreateActiveSession(userId);
        SseEmitter emitter = new SseEmitter(120_000L);

        CompletableFuture.runAsync(() -> {
            try {
                AiChatResponse response = aiChatService.chat(
                        userId,
                        session.getId(),
                        aiChatRequest.getQuery(),
                        aiChatRequest.getModuleType()
                );
                updateAnswerSource(session.getId(), response.getAnswerSource());
                sendMetadata(emitter, response);
                streamAnswer(emitter, response.getAnswer());
                emitter.send(SseEmitter.event().name("done").data(response));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 发送语音消息
     */
    @PostMapping("/voice")
    public Result<String> sendVoice(@RequestParam("audioFile") MultipartFile audioFile,
                                    HttpServletRequest request) {
        Long userId = getUserIdOpt(request);
        ChatSession session = chatSessionService.getOrCreateActiveSession(userId);

        // 1. (2.2模块) 将用户传上来的音频（Blob）进行语音识别
        String queryText = audioService.speechToText(audioFile);
        
        // 埋点统计（4.1 模块）：纯寒暄不进入热词榜
        if (!CasualConversationUtils.isCasualOnly(queryText)) {
            statHotQuestionService.recordQuestion(queryText);
        }

        // 如果处于人工服务状态，直接发送识别文本给客服
        if (session.getStatus() == 1) {
            saveMessage(session.getId(), 1, 2, queryText, "url_to_user_audio");
            String wsAdminMsg = String.format("{\"type\":\"USER_MSG\", \"sessionId\":%d, \"userId\":%d, \"content\":\"%s(语音)\"}", session.getId(), userId, queryText.replace("\"", "\\\""));
            if (session.getAdminId() != null) {
                ChatWebSocketServer.sendMessageToAdmin(String.valueOf(session.getAdminId()), wsAdminMsg);
            }
            return Result.success("识别内容已发给人工客服: [" + queryText + "]");
        }

        // 2. 保存用户语音对应的识别结果
        saveMessage(session.getId(), 1, 2, queryText, "url_to_user_audio");

        // 3. 到智能问答核心模块进行推理
        AiChatResponse aiChatResponse = aiChatService.chat(userId, session.getId(), queryText, null);
        String aiAnswer = aiChatResponse.getAnswer();

        // 【2.4模块】业务联动自动推送 API：对语音识别出的文本做关键联动匹配
        aiAnswer = bizContactService.appendContactInfoIfMatch(queryText, aiAnswer);

        // 4. (2.2模块) 默认对语音发问都生成对应的语音回答，以打造完整的双模态极佳体验
        String responseMediaUrl = audioService.textToSpeech(aiAnswer);

        // 5. 保存 AI 的回复
        saveMessage(session.getId(), 2, 2, aiAnswer, responseMediaUrl);
        updateAnswerSource(session.getId(), aiChatResponse.getAnswerSource());

        // 前端拿到这个返回对象后，可以直接展示 aiAnswer 文字，同时通过 responseMediaUrl 利用 AVKit 播报。
        return Result.success("识别内容: [" + queryText + "] \nAI回复: " + aiAnswer + " \n播放地址: " + responseMediaUrl);
    }

    /**
     * 心理指导：使用本地生成模型输出轻松、安抚型回复，不再依赖 Coze 工作流。
     */
    @PostMapping("/psychological")
    public Result<String> psychologicalCounseling(@RequestParam("studentMsg") String studentMsg,
                                                  HttpServletRequest request) {
        Long userId = getUserIdOpt(request);
        ChatSession session = chatSessionService.getOrCreateActiveSession(userId);

        saveMessage(session.getId(), 1, 1, studentMsg, null);
        String aiAnswer;
        try {
            aiAnswer = localModelClient.psychologicalCounseling(studentMsg);
        } catch (Exception ex) {
            aiAnswer = LocalModelClient.superFallbackPsychologicalCounseling(studentMsg);
        }
        saveMessage(session.getId(), 2, 1, aiAnswer, null);
        updateAnswerSource(session.getId(), "LOCAL_MODEL_PSYCHOLOGY");

        return Result.success(aiAnswer);
    }

    /**
     * 【2.3模块】用户申请转人工客服
     */
    @PostMapping("/transfer-to-human")
    public Result<String> transferToHuman(HttpServletRequest request) {
        Long userId = getUserIdOpt(request);
        ChatSession session = chatSessionService.getOrCreateActiveSession(userId);

        if (session.getStatus() == 1) {
            return Result.error("当前已经处于人工客服服务中，请耐心等待回复。");
        }

        // 尝试分配在线客服
        String adminIdStr = ChatWebSocketServer.getAvailableAdmin();
        if (adminIdStr == null) {
            return Result.error("当前无在线客服，请稍后再试或继续咨询AI助手。");
        }

        Long adminId = Long.valueOf(adminIdStr);

        // 修改会话状态为转人工
        session.setStatus(1);
        session.setAdminId(adminId);
        chatSessionService.updateById(session);

        // 通过 WebSocket 给分配到的客服发个通知
        String notification = String.format("{\"type\":\"NEW_TASK\", \"sessionId\":%d, \"userId\":%d, \"msg\":\"用户请求人工介入\"}", session.getId(), userId);
        ChatWebSocketServer.sendMessageToAdmin(adminIdStr, notification);

        return Result.success("已成功为您转接人工客服，正在排队等待回复...");
    }

    /**
     * 【2.3模块】客服在控制台给用户发送回复
     */
    @PostMapping("/admin/reply")
    public Result<String> adminReply(@RequestParam("sessionId") Long sessionId,
                                     @RequestParam("content") String content,
                                     HttpServletRequest request) {
        // 注：此处理应校验登录者是否为有效的Admin角色
        Long adminId = getUserIdOpt(request);
        ChatSession session = chatSessionService.getById(sessionId);

        if (session == null || session.getStatus() != 1) {
            return Result.error("会话不存在或并非处于人工服务中。");
        }

        // 1. 保存客服回复的消息至数据库
        saveMessage(session.getId(), 3, 1, content, null); // senderType=3(客服), msgType=1(文本)

        // 2. 通过 WebSocket 即时推送给发起提问的用户前端
        String wsUserMsg = String.format("{\"type\":\"ADMIN_REPLY\", \"adminId\":%d, \"content\":\"%s\"}", adminId, content.replace("\"", "\\\""));
        ChatWebSocketServer.sendMessageToUser(String.valueOf(session.getUserId()), wsUserMsg);

        return Result.success("回复成功");
    }

    /**
     * 【2.3模块】客服结束会话，交还给AI
     */
    @PostMapping("/admin/finish")
    public Result<String> adminFinish(@RequestParam("sessionId") Long sessionId) {
        ChatSession session = chatSessionService.getById(sessionId);
        if (session != null && session.getStatus() == 1) {
            session.setStatus(0); // 0-AI托管
            session.setAdminId(null);
            chatSessionService.updateById(session);

            // 推送通知给用户，人工服务已结束
            String wsUserMsg = "{\"type\":\"SYSTEM_NOTICE\", \"content\":\"人工服务已结束，已为您重新交接给AI托管模式。\"}";
            ChatWebSocketServer.sendMessageToUser(String.valueOf(session.getUserId()), wsUserMsg);

            return Result.success("已结束人工干预，会话交回AI");
        }
        return Result.error("操作失败：该会话不在人工服务状态");
    }

    // 通用的保存消息工具方法
    private void saveMessage(Long sessionId, Integer senderType, Integer msgType, String content, String mediaUrl) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setSenderType(senderType);
        msg.setMsgType(msgType);
        msg.setContent(content);
        msg.setMediaUrl(mediaUrl);
        msg.setCreatedAt(LocalDateTime.now());
        chatMessageService.save(msg);
        
        // 顺便更新一下 Session 的 updatedAt
        ChatSession s = new ChatSession();
        s.setId(sessionId);
        s.setUpdatedAt(LocalDateTime.now());
        chatSessionService.updateById(s);
    }

    private void updateAnswerSource(Long sessionId, String answerSource) {
        ChatSession s = new ChatSession();
        s.setId(sessionId);
        s.setAnswerSource(answerSource);
        s.setUpdatedAt(LocalDateTime.now());
        chatSessionService.updateById(s);
    }

    private void sendMetadata(SseEmitter emitter, AiChatResponse response) throws IOException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("rewriteQuestion", response.getRewriteQuestion());
        metadata.put("moduleType", response.getModuleType());
        metadata.put("hitStatus", response.getHitStatus());
        metadata.put("hitLabel", response.getHitLabel());
        metadata.put("topScore", response.getTopScore());
        metadata.put("answerSource", response.getAnswerSource());
        emitter.send(SseEmitter.event().name("metadata").data(metadata));
    }

    private void streamAnswer(SseEmitter emitter, String answer) throws IOException, InterruptedException {
        if (answer == null) {
            return;
        }
        int chunkSize = 24;
        for (int i = 0; i < answer.length(); i += chunkSize) {
            String chunk = answer.substring(i, Math.min(i + chunkSize, answer.length()));
            emitter.send(SseEmitter.event().name("answer").data(chunk));
            Thread.sleep(15);
        }
    }

    /**
     * 【4.2模块】主动推送常见问题
     * 适合在用户初次打开对话窗口时拉取，作为快速点击的 Suggestion 气泡
     */
    @GetMapping("/suggested-questions")
    public Result<List<Map<String, Object>>> getSuggestedQuestions(
            @RequestParam(defaultValue = "3") int limit) {
        return Result.success(statHotQuestionService.getHotQuestions(30, limit));
    }

    @GetMapping("/common-questions")
    public Result<List<Map<String, Object>>> getCommonQuestions(
            @RequestParam(defaultValue = "5") int limit) {
        return Result.success(statHotQuestionService.getRandomQuestionAnswers(limit));
    }

    /**
     * 【4.3模块】上报未识别/兜底提问
     * 机器人回答 "我不知道" 之类的兜底话术后，给用户一个“点踩反馈/上报错漏”按钮
     */
    @PostMapping("/report-unrecognized")
    public Result<String> reportUnrecognized(@RequestParam("query") String query,
                                             HttpServletRequest request) {
        Long userId = getUserIdOpt(request);
        unrecognizedQueryService.recordUnrecognized(userId, query);
        return Result.success("已记录反馈，管理员将尽快根据您的提问完善系统知识库");
    }
}
