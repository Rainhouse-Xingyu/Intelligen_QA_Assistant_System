package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.rainhouse.qasystem.entity.ChatMessage;
import me.rainhouse.qasystem.mapper.ChatMessageMapper;
import me.rainhouse.qasystem.service.ChatMemoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ChatMemoryServiceImpl implements ChatMemoryService {

    private static final Set<String> CONTEXT_REFERENCE_WORDS = Set.of(
            "刚才", "上面", "之前", "前面", "这个", "那个", "这些", "那些",
            "它", "他", "她", "其", "继续", "还有", "呢"
    );

    private static final Set<String> FOLLOW_UP_ONLY_WORDS = Set.of(
            "什么时候", "怎么办", "是什么", "怎么样", "怎么", "如何", "哪里", "多少",
            "时间", "截止", "条件", "流程", "材料", "要求", "办理", "申请", "查询", "查看",
            "需要", "什么", "是否", "可以", "具体", "继续", "还有", "然后", "之后", "一下",
            "吗", "呢", "的", "到"
    );
    private static final int MAX_CONTEXT_USER_MESSAGES = 3;

    private final ChatMessageMapper chatMessageMapper;
    private final int maxMessages;
    private final int maxContextChars;
    private final int maxRetrievalChars;

    public ChatMemoryServiceImpl(ChatMessageMapper chatMessageMapper,
                                 @Value("${chat.memory.max-messages:12}") int maxMessages,
                                 @Value("${chat.memory.max-context-chars:1200}") int maxContextChars,
                                 @Value("${chat.memory.max-retrieval-chars:800}") int maxRetrievalChars) {
        this.chatMessageMapper = chatMessageMapper;
        this.maxMessages = Math.max(2, maxMessages);
        this.maxContextChars = Math.max(200, maxContextChars);
        this.maxRetrievalChars = Math.max(200, maxRetrievalChars);
    }

    @Override
    public String buildRecentContext(Long sessionId, String currentQuery) {
        if (sessionId == null) {
            return "";
        }

        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getSenderType, 1)
                .orderByDesc(ChatMessage::getCreatedAt)
                .orderByDesc(ChatMessage::getId)
                .last("limit " + Math.max(maxMessages + 2, 4)));

        if (messages == null || messages.isEmpty()) {
            return "";
        }

        Collections.reverse(messages);
        List<ChatMessage> usableMessages = new ArrayList<>(messages);
        removeCurrentQuestionIfPresent(usableMessages, currentQuery);
        if (usableMessages.size() > MAX_CONTEXT_USER_MESSAGES) {
            usableMessages = new ArrayList<>(usableMessages.subList(
                    usableMessages.size() - MAX_CONTEXT_USER_MESSAGES,
                    usableMessages.size()));
        }

        StringBuilder context = new StringBuilder();
        for (ChatMessage message : usableMessages) {
            String content = normalizeContent(message.getContent());
            if (!StringUtils.hasText(content)) {
                continue;
            }
            String line = label(message.getSenderType()) + "：" + content;
            if (context.length() + line.length() + 1 > maxContextChars) {
                int overflow = context.length() + line.length() + 1 - maxContextChars;
                if (overflow < context.length()) {
                    context.delete(0, overflow);
                }
            }
            if (context.length() > 0) {
                context.append('\n');
            }
            context.append(line);
        }
        return context.toString().trim();
    }

    @Override
    public String buildRetrievalQuery(String query, String memoryContext) {
        String cleanQuery = normalizeContent(query);
        if (!StringUtils.hasText(cleanQuery) || !StringUtils.hasText(memoryContext)) {
            return cleanQuery;
        }
        if (!looksLikeFollowUp(cleanQuery)) {
            return cleanQuery;
        }
        return limit("对话上下文：\n" + memoryContext + "\n当前问题：" + cleanQuery, maxRetrievalChars);
    }

    @Override
    public String buildGenerationQuestion(String originalQuestion, String memoryContext) {
        String cleanQuestion = normalizeContent(originalQuestion);
        if (!StringUtils.hasText(cleanQuestion) || !StringUtils.hasText(memoryContext)) {
            return cleanQuestion;
        }
        if (!looksLikeFollowUp(cleanQuestion)) {
            return cleanQuestion;
        }
        return limit("对话上下文（用于理解当前追问，不作为政策依据）：\n"
                + memoryContext
                + "\n\n当前用户问题："
                + cleanQuestion, maxContextChars + 300);
    }

    private void removeCurrentQuestionIfPresent(List<ChatMessage> messages, String currentQuery) {
        String normalizedCurrent = normalizeForCompare(currentQuery);
        if (!StringUtils.hasText(normalizedCurrent)) {
            return;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.getSenderType() != null
                    && message.getSenderType() == 1
                    && normalizedCurrent.equals(normalizeForCompare(message.getContent()))) {
                messages.remove(i);
                return;
            }
        }
    }

    private boolean looksLikeFollowUp(String query) {
        String normalized = normalizeForCompare(query);
        if (containsAny(normalized, CONTEXT_REFERENCE_WORDS)) {
            return true;
        }
        String remaining = normalized;
        List<String> removableWords = FOLLOW_UP_ONLY_WORDS.stream()
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .toList();
        for (String word : removableWords) {
            remaining = remaining.replace(word.toLowerCase(Locale.ROOT), "");
        }
        return remaining.length() <= 1;
    }

    private boolean containsAny(String text, Set<String> words) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        for (String word : words) {
            if (text.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String label(Integer senderType) {
        if (senderType == null) {
            return "消息";
        }
        return switch (senderType) {
            case 1 -> "用户";
            case 2 -> "助手";
            case 3 -> "人工客服";
            default -> "消息";
        };
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.replace('\r', '\n')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeForCompare(String text) {
        return normalizeContent(text)
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String limit(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(text.length() - maxChars);
    }
}
