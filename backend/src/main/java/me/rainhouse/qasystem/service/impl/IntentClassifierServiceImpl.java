package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.service.IntentClassifierService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IntentClassifierServiceImpl implements IntentClassifierService {

    private final Map<String, List<String>> moduleKeywords = new LinkedHashMap<>();

    public IntentClassifierServiceImpl(AiModelProperties aiModelProperties) {
        // 预留 MacBERT 本地意图分类模型接入点；当前实现保持纯 Java 可运行。
        String ignoredModelPath = aiModelProperties.getIntentClassifierPath();
        moduleKeywords.put("考务通知", List.of("考试", "考场", "监考", "准考证", "补考", "缓考", "作弊", "成绩", "四六级"));
        moduleKeywords.put("教学运行", List.of("选课", "课表", "调课", "重修", "学分", "教室", "课程", "培养方案"));
        moduleKeywords.put("学业帮扶", List.of("挂科", "绩点", "gpa", "预警", "学习", "复习", "帮扶", "资源"));
        moduleKeywords.put("心理辅导", List.of("焦虑", "压力", "失眠", "难受", "抑郁", "心理", "情绪", "崩溃"));
    }

    @Override
    public String classify(String query, String userSelectedModule) {
        if (StringUtils.hasText(userSelectedModule)) {
            return userSelectedModule.trim();
        }
        String text = query == null ? "" : query.toLowerCase();
        String bestModule = null;
        int bestScore = 0;
        for (Map.Entry<String, List<String>> entry : moduleKeywords.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword.toLowerCase())) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestModule = entry.getKey();
            }
        }
        return bestModule;
    }
}
