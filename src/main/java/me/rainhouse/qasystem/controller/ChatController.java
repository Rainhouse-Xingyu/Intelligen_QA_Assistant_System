package me.rainhouse.qasystem.controller;

import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.entity.ChatMessage;
import me.rainhouse.qasystem.entity.ChatSession;
import me.rainhouse.qasystem.service.AudioService;
import me.rainhouse.qasystem.service.ChatMessageService;
import me.rainhouse.qasystem.service.ChatSessionService;
import me.rainhouse.qasystem.service.CozeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private CozeService cozeService;

    @Autowired
    private AudioService audioService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

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
                                   @RequestParam(value = "needTts", defaultValue = "false") Boolean needTts,
                                   HttpServletRequest request) {
        
        Long userId = getUserIdOpt(request);
        ChatSession session = chatSessionService.getOrCreateActiveSession(userId);

        // 1. 保存用户的文本问题
        saveMessage(session.getId(), 1, 1, query, null);

        // 2. 将问题通过 2.1 模块的 Coze 意图网关发给 AI
        String aiAnswer = cozeService.chat(String.valueOf(userId), query);

        // 3. 将 AI 答案存入库
        String mediaUrl = null;
        
        // 4. (2.2模块) 判断是否需要播报语音
        if (needTts) {
            mediaUrl = audioService.textToSpeech(aiAnswer);
        }
        saveMessage(session.getId(), 2, needTts ? 2 : 1, aiAnswer, mediaUrl);

        return Result.success(aiAnswer);
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

        // 2. 保存用户语音对应的识别结果
        saveMessage(session.getId(), 1, 2, queryText, "url_to_user_audio");

        // 3. (2.1模块) 到 AI 网关进行推理
        String aiAnswer = cozeService.chat(String.valueOf(userId), queryText);

        // 4. (2.2模块) 默认对语音发问都生成对应的语音回答，以打造完整的双模态极佳体验
        String responseMediaUrl = audioService.textToSpeech(aiAnswer);

        // 5. 保存 AI 的回复
        saveMessage(session.getId(), 2, 2, aiAnswer, responseMediaUrl);

        // 前端拿到这个返回对象后，可以直接展示 aiAnswer 文字，同时通过 responseMediaUrl 利用 AVKit 播报。
        return Result.success("识别内容: [" + queryText + "] \nAI回复: " + aiAnswer + " \n播放地址: " + responseMediaUrl);
    }

    /**
     * 通用的保存消息工具方法
     */
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
}