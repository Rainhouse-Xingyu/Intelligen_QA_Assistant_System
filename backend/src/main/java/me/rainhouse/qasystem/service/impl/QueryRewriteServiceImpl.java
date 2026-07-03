package me.rainhouse.qasystem.service.impl;

import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.common.utils.CasualConversationUtils;
import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.service.QueryRewriteService;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class QueryRewriteServiceImpl implements QueryRewriteService {

    private final LocalModelClient localModelClient;
    private final Map<String, String> policyTopics = new LinkedHashMap<>();

    public QueryRewriteServiceImpl(AiModelProperties aiModelProperties,
                                   LocalModelClient localModelClient) {
        this.localModelClient = localModelClient;
        aiModelProperties.getQwenCleanerPath();
        policyTopics.put("补考|缓考|考试|考场|准考证|成绩|四六级", "考务");
        policyTopics.put("选课|退课|补选|课表|调课|重修|学分|培养方案", "教学运行");
        policyTopics.put("挂科|绩点|学业预警|帮扶|困难|留级|毕业", "学业帮扶");
        policyTopics.put("请假|销假|休学|复学|转专业|学籍|证明", "学籍事务");
        policyTopics.put("心理|焦虑|压力|失眠|咨询|情绪", "心理辅导");
    }

    @Override
    public String rewrite(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }
        if (CasualConversationUtils.isCasualOnly(query)) {
            return query.trim();
        }
        if (localModelClient.enabled()) {
            try {
                String rewritten = normalizeQuery(localModelClient.rewrite(query));
                if (isMeaningfulRewrite(query, rewritten)) {
                    log.info("[AI] /rewrite 本地模型完成：{}", rewritten);
                    return limit(ensureQuestionMark(rewritten));
                }
                log.info("[AI] /rewrite 本地模型结果不够充分，使用规则改写：{}", rewritten);
            } catch (Exception e) {
                log.warn("[AI] /rewrite 本地模型调用失败，使用规则改写: {}", e.getMessage());
            }
        }
        return limit(ruleRewrite(query));
    }

    private String ruleRewrite(String query) {
        String normalized = normalizeQuery(query);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }

        String core = removeQuestionWords(normalized);
        String topic = detectTopic(normalized);
        if (!StringUtils.hasText(core)) {
            core = topic;
        }
        core = compactCore(core);

        if (containsAny(normalized, "什么时候", "啥时候", "时间", "截止", "几号", "日期", "安排")) {
            return ensureQuestionMark(core + "的时间安排、截止要求和查询方式是什么");
        }
        if (containsAny(normalized, "怎么", "如何", "咋", "怎么办", "流程", "申请", "办理", "提交", "弄")) {
            return ensureQuestionMark(core + "的办理流程、申请条件和所需材料是什么");
        }
        if (containsAny(normalized, "条件", "要求", "资格", "对象", "范围", "能不能", "可以吗", "是否可以")) {
            return ensureQuestionMark(core + "的适用条件、政策要求和注意事项是什么");
        }
        if (containsAny(normalized, "在哪", "哪里", "入口", "查询", "查看")) {
            return ensureQuestionMark(core + "的查询入口、办理地点或查看方式是什么");
        }

        if (normalized.length() <= 16 || !endsWithQuestionMark(normalized)) {
            return ensureQuestionMark("关于" + core + "，学校政策中的适用范围、办理要求和注意事项是什么");
        }
        return ensureQuestionMark(normalized);
    }

    private String normalizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }
        return query.replace('\r', '\n')
                .replaceAll("\\s+", " ")
                .replaceAll("^[你好您好麻烦请问一下问下我想咨询咨询一下,，。\\s]+", "")
                .replaceAll("[？！?!.。；;]+$", "")
                .trim();
    }

    private String removeQuestionWords(String query) {
        return query.replaceAll("(我想|想问|咨询|请问|问一下|问下|这个|那个|一下|相关|政策|规定)", "")
                .replaceAll("(有什么要求|有哪些要求|什么要求|有什么条件|有哪些条件|什么条件|怎么弄|怎么办|怎么|如何|咋|能不能|可以吗|是否可以|什么时候|啥时候|在哪|哪里|入口|查询|查看)", "")
                .replaceAll("[,，。！？?\\s]+", "")
                .trim();
    }

    private String detectTopic(String query) {
        for (Map.Entry<String, String> entry : policyTopics.entrySet()) {
            if (query.matches(".*(" + entry.getKey() + ").*")) {
                return entry.getValue();
            }
        }
        return "教务问题";
    }

    private String compactCore(String core) {
        String value = StringUtils.hasText(core) ? core : "教务问题";
        if (value.length() > 36) {
            return value.substring(0, 36);
        }
        return value;
    }

    private boolean isMeaningfulRewrite(String original, String rewritten) {
        if (!StringUtils.hasText(rewritten)) {
            return false;
        }
        String normalizedOriginal = normalizeQuery(original);
        String normalizedRewrite = normalizeQuery(rewritten);
        if (!StringUtils.hasText(normalizedRewrite)) {
            return false;
        }
        if (normalizedRewrite.equals(normalizedOriginal)) {
            return normalizedRewrite.length() > 14
                    && containsAny(normalizedRewrite, "流程", "条件", "要求", "时间", "查询", "政策");
        }
        if (hasTopicDrift(normalizedOriginal, normalizedRewrite)) {
            return false;
        }
        return normalizedRewrite.length() >= Math.min(8, normalizedOriginal.length() + 2)
                && containsAny(normalizedRewrite, "流程", "条件", "要求", "时间", "查询", "政策", "办理", "材料", "安排", "注意事项", "规则", "入口", "截止");
    }

    private boolean hasTopicDrift(String original, String rewritten) {
        String originalTopic = detectTopic(original);
        if ("教务问题".equals(originalTopic)) {
            return false;
        }
        String rewrittenTopic = detectTopic(rewritten);
        if (originalTopic.equals(rewrittenTopic)) {
            return false;
        }
        return originalTopicKeywords(original).stream().noneMatch(rewritten::contains);
    }

    private java.util.List<String> originalTopicKeywords(String query) {
        return policyTopics.keySet().stream()
                .flatMap(pattern -> Arrays.stream(pattern.split("\\|")))
                .filter(query::contains)
                .toList();
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String ensureQuestionMark(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (endsWithQuestionMark(trimmed)) {
            return trimmed;
        }
        return trimmed + "？";
    }

    private boolean endsWithQuestionMark(String text) {
        return text.endsWith("？") || text.endsWith("?");
    }

    private String limit(String text) {
        return text.length() <= 500 ? text : text.substring(0, 500);
    }
}
